package at.esoren.vystormgallery;

import Vystorm.vystorm_core._coreMethods.dataStorage.AtomicFilesInterface;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;

/** Exhibition metadata only; the actual paintings retain their original locks and world ownership. */
final class ExhibitionStore {
    record PaintingRef(UUID entity, UUID world, int x, int y, int z, String motif) {}
    record Exhibition(UUID id, UUID owner, String name, String title, String description, boolean published,
                      List<PaintingRef> paintings, Set<UUID> votes) {
        Exhibition { paintings = List.copyOf(paintings); votes = Set.copyOf(votes); }
        boolean visible(UUID viewer) { return published || owner.equals(viewer); }
    }
    enum Result { SAVED, MISSING, DENIED, INVALID, LIMIT, UNAVAILABLE }
    private final Path file; private final Logger log; private final AtomicFilesInterface files;
    private Map<UUID, Exhibition> entries = new LinkedHashMap<>(); private boolean readOnly;
    /** @param files Core's file part, {@code api.data().files()} */
    ExhibitionStore(Path file, Logger log, AtomicFilesInterface files) {
        this.file = file; this.log = log; this.files = files;
        if (!Files.exists(file)) return;
        try {
            YamlConfiguration y = new YamlConfiguration(); y.load(file.toFile());
            var section = y.getConfigurationSection("exhibitions");
            if (section == null) throw new IllegalArgumentException("missing exhibitions section");
            for (String key : section.getKeys(false)) {
                String p = "exhibitions." + key + "."; UUID id = UUID.fromString(key), owner = UUID.fromString(y.getString(p + "owner", ""));
                if (!y.isString(p + "name") || !y.isString(p + "title") || !y.isString(p + "description")) throw new IllegalArgumentException("invalid exhibition texts");
                String name = y.getString(p + "name", ""), title = y.getString(p + "title", ""), description = y.getString(p + "description", "");
                if (!name.matches("[A-Za-z0-9_-]{1,24}") || !text(title, 64, false) || !text(description, 240, true) || own(owner, name) != null) throw new IllegalArgumentException("invalid exhibition");
                if (!(y.get(p + "paintings") instanceof List<?> rawPaintings) || rawPaintings.stream().anyMatch(r -> !(r instanceof Map<?, ?>))
                        || !(y.get(p + "votes") instanceof List<?> rawVotes) || rawVotes.stream().anyMatch(r -> !(r instanceof String))
                        || !y.isBoolean(p + "published") || entries.values().stream().filter(e -> e.owner().equals(owner)).count() >= 10)
                    throw new IllegalArgumentException("invalid exhibition data");
                List<PaintingRef> paintings = new ArrayList<>();
                for (Map<?, ?> raw : y.getMapList(p + "paintings")) {
                    if (!(raw.get("motif") instanceof String motif) || !(raw.get("entity") instanceof String) || !(raw.get("world") instanceof String)) throw new IllegalArgumentException("invalid painting reference");
                    paintings.add(new PaintingRef(UUID.fromString(raw.get("entity").toString()), UUID.fromString(raw.get("world").toString()),
                            coordinate(raw.get("x")), coordinate(raw.get("y")), coordinate(raw.get("z")), motif));
                }
                Set<UUID> votes = new HashSet<>(); for (String voter : y.getStringList(p + "votes")) votes.add(UUID.fromString(voter));
                if (paintings.size() > 60 || paintings.stream().map(PaintingRef::entity).distinct().count() != paintings.size()) throw new IllegalArgumentException("invalid paintings");
                entries.put(id, new Exhibition(id, owner, name, title, description, y.getBoolean(p + "published"), paintings, votes));
            }
        } catch (Exception failure) { entries.clear(); readOnly = true; log.warning("Exhibitions retained unchanged; editing disabled after load failure: " + failure.getMessage()); }
    }
    /** A whole block coordinate as written by {@link #save}: Integer or Long within the int range, never fractional. */
    static int coordinate(Object raw) {
        if (raw instanceof Integer i) return i;
        if (raw instanceof Long l && l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) return (int) (long) l;
        throw new IllegalArgumentException("invalid coordinate " + raw);
    }
    static boolean text(String value, int max, boolean empty) {
        return value != null && (empty || !value.isBlank()) && value.codePointCount(0, value.length()) <= max
                && value.codePoints().noneMatch(c -> Character.isISOControl(c) || c == 0xa7 || c == 0x2028 || c == 0x2029);
    }
    Exhibition get(UUID id) { return entries.get(id); }
    Exhibition own(UUID owner, String name) { return entries.values().stream().filter(e -> e.owner().equals(owner) && e.name().equalsIgnoreCase(name)).findFirst().orElse(null); }
    List<Exhibition> visible(UUID viewer) { return entries.values().stream().filter(e -> e.visible(viewer)).sorted(Comparator.comparingInt((Exhibition e) -> e.votes().size()).reversed().thenComparing(Exhibition::name)).toList(); }
    Result create(UUID owner, String name) {
        if (readOnly) return Result.UNAVAILABLE;
        if (name == null || !name.matches("[A-Za-z0-9_-]{1,24}") || own(owner, name) != null) return Result.INVALID;
        if (entries.values().stream().filter(e -> e.owner().equals(owner)).count() >= 10) return Result.LIMIT;
        return save(new Exhibition(UUID.randomUUID(), owner, name, name, "", false, List.of(), Set.of()), false);
    }
    private Result edit(UUID actor, Exhibition e) { return readOnly ? Result.UNAVAILABLE : e == null ? Result.MISSING : !e.owner().equals(actor) ? Result.DENIED : null; }
    Result describe(UUID actor, UUID id, String value, boolean title) {
        Exhibition e = get(id); Result error = edit(actor, e); if (error != null) return error;
        if (!text(value, title ? 64 : 240, !title)) return Result.INVALID;
        return save(new Exhibition(id, actor, e.name(), title ? value.strip() : e.title(), title ? e.description() : value.strip(), e.published(), e.paintings(), e.votes()), false);
    }
    Result painting(UUID actor, UUID id, PaintingRef painting, boolean add) {
        Exhibition e = get(id); Result error = edit(actor, e); if (error != null) return error;
        if (painting == null) return Result.INVALID;
        List<PaintingRef> paintings = new ArrayList<>(e.paintings());
        int at = -1;
        for (int i = 0; i < paintings.size(); i++) if (paintings.get(i).entity().equals(painting.entity())) { at = i;break; }
        if (add) {
            if (at >= 0) paintings.set(at, painting); // already shown: keep its place
            else { if (paintings.size() >= 60) return Result.LIMIT; paintings.add(painting); }
        } else if (at >= 0) paintings.remove(at);
        if (paintings.equals(e.paintings())) return Result.SAVED; // nothing changed: keep votes and order
        return save(new Exhibition(id, actor, e.name(), e.title(), e.description(), e.published() && !paintings.isEmpty(), paintings, Set.of()), false);
    }
    Result publish(UUID actor, UUID id, boolean published) {
        Exhibition e = get(id); Result error = edit(actor, e); if (error != null) return error;
        if (published && e.paintings().isEmpty()) return Result.INVALID;
        return save(new Exhibition(id, actor, e.name(), e.title(), e.description(), published, e.paintings(), e.votes()), false);
    }
    Result vote(UUID actor, UUID id) {
        Exhibition e = get(id); if (readOnly) return Result.UNAVAILABLE;
        if (e == null) return Result.MISSING;
        if (!e.published() || e.paintings().isEmpty() || e.owner().equals(actor)) return Result.DENIED;
        Set<UUID> votes = new HashSet<>(e.votes()); if (!votes.remove(actor)) votes.add(actor);
        return save(new Exhibition(id, e.owner(), e.name(), e.title(), e.description(), e.published(), e.paintings(), votes), false);
    }
    Result delete(UUID actor, UUID id) { Exhibition e = get(id); Result error = edit(actor, e); return error == null ? save(e, true) : error; }
    private Result save(Exhibition changed, boolean remove) {
        if (readOnly) return Result.UNAVAILABLE;
        Map<UUID, Exhibition> next = new LinkedHashMap<>(entries); if (remove) next.remove(changed.id()); else next.put(changed.id(), changed);
        YamlConfiguration y = new YamlConfiguration(); y.createSection("exhibitions");
        for (Exhibition e : next.values()) {
            String p = "exhibitions." + e.id() + ".";
            y.set(p + "owner", e.owner().toString()); y.set(p + "name", e.name()); y.set(p + "title", e.title()); y.set(p + "description", e.description());
            y.set(p + "published", e.published()); y.set(p + "votes", e.votes().stream().map(UUID::toString).sorted().toList());
            y.set(p + "paintings", e.paintings().stream().map(r -> Map.of("entity", r.entity().toString(), "world", r.world().toString(), "x", r.x(), "y", r.y(), "z", r.z(), "motif", r.motif())).toList());
        }
        try { files.write(file, y); entries = next; return Result.SAVED; }
        catch (java.io.IOException failure) { log.warning("Exhibition changes not saved: " + failure.getMessage()); return Result.UNAVAILABLE; }
    }
}
