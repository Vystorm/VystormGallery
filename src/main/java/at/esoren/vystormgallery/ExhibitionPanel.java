package at.esoren.vystormgallery;

import Vystorm.vystorm_core.panel.*;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Exhibition browsing and owner tools, rendered from immutable snapshots. */
final class ExhibitionPanel {
    static final String BROWSER = "exhibitions", DETAIL = "exhibition";
    static final int PAGE_SIZE = 9;
    record Entry(UUID id, String title, String description, int paintings, int votes, boolean published) {}
    record PaintingRow(UUID id, String motif, String location, boolean loaded) {}
    record Overview(Entry entry, List<PaintingRow> paintings, boolean owner, boolean voted, boolean tour) {
        Overview { paintings = List.copyOf(paintings); }
    }
    interface Actions {
        boolean allowed(Player player, UUID exhibition);
        Result change(Player player, UUID exhibition, String action, String value);
        void open(Player player, UUID exhibition);
        void tour(Player player, UUID exhibition);
    }
    private final Map<UUID, String> selected = new HashMap<>();
    private final Map<UUID, Integer> pages = new HashMap<>();
    void forget(UUID player) { selected.remove(player); pages.remove(player); }
    private Ui.Change<String> select(PanelContext ctx, UUID exhibition, Actions actions) {
        return (a, key) -> {
            if (!actions.allowed(a.player(), exhibition)) return Result.error(ctx.text("denied"));
            selected.put(a.player().getUniqueId(), key); a.refresh(); return Result.ok();
        };
    }
    private Ui.Change<Integer> turn(PanelContext ctx, UUID exhibition, Actions actions) {
        return (a, page) -> {
            if (!actions.allowed(a.player(), exhibition)) return Result.error(ctx.text("denied"));
            pages.put(a.player().getUniqueId(), page); selected.remove(a.player().getUniqueId()); a.refresh(); return Result.ok();
        };
    }
    Panel browser(PanelContext ctx, List<Entry> entries, Actions actions) {
        UUID player = ctx.player().getUniqueId();
        return browser(ctx, entries, selected.get(player), pages.getOrDefault(player, 0), actions, select(ctx, null, actions), turn(ctx, null, actions));
    }
    Panel detail(PanelContext ctx, Overview o, Actions actions) {
        UUID player = ctx.player().getUniqueId();
        return detail(ctx, o, selected.get(player), pages.getOrDefault(player, 0), actions, select(ctx, o.entry().id(), actions), turn(ctx, o.entry().id(), actions));
    }
    static Panel browser(PanelContext ctx, List<Entry> entries, String selected, int requestedPage, Actions actions,
                         Ui.Change<String> select, Ui.Change<Integer> turn) {
        int pages = pages(entries.size()), page = Math.clamp(requestedPage, 0, pages - 1);
        List<Entry> rows = slice(entries, page);
        Entry chosen = rows.stream().filter(r -> r.id().toString().equals(selected)).findFirst().orElse(rows.isEmpty() ? null : rows.getFirst());
        Ui.ListView list = Ui.list("exhibitions").items(rows, r -> Ui.listItem(r.id().toString(), r.title()).icon(Ui.item(Material.PAINTING))
                .subtitle(ctx.text("exhibitions.summary", "count", r.paintings(), "votes", r.votes())))
                .selected(chosen == null ? null : chosen.id().toString()).empty(ctx.text("panel.empty")).onSelect(select).grow(2);
        Ui.Box detail = Ui.col().gap(6).grow(3);
        if (chosen != null) detail.children(Ui.heading(chosen.title()), Ui.text(chosen.description()), status(ctx, chosen), stats(ctx, chosen),
                Ui.button("open").label(ctx.text("panel.open")).icon(Ui.item(Material.PAINTING)).primary()
                        .onClick(a -> { a.closeAndThen(p -> actions.open(p, chosen.id())); return Result.ok(); }));
        Ui.Form create = Ui.form("create", ctx.text("exhibitions.create"))
                .child(Ui.input("name", ctx.text("exhibitions.name-prompt")).maxLength(24))
                .onSubmit((a, values) -> MotifPanel.refresh(a, actions.change(a.player(), null, "create", value(values, "name"))));
        Panel panel = Panel.page(ctx.text("exhibitions.title")).subtitle(ctx.text("exhibitions.description"))
                .icon(Ui.item(Material.PAINTING)).size(Panel.Size.LARGE)
                .child(Ui.tabs("exhibition-tabs").tab("browse", ctx.text("panel.browse"), Ui.row().gap(8).children(list, detail))
                        .tab("create", ctx.text("exhibitions.create"), create));
        return footer(ctx, panel, page, pages, turn);
    }
    static Panel detail(PanelContext ctx, Overview o, String selected, int requestedPage, Actions actions,
                        Ui.Change<String> select, Ui.Change<Integer> turn) {
        Entry e = o.entry();
        int pages = pages(o.paintings().size()), page = Math.clamp(requestedPage, 0, pages - 1);
        List<PaintingRow> rows = slice(o.paintings(), page);
        PaintingRow chosen = rows.stream().filter(r -> r.id().toString().equals(selected)).findFirst().orElse(rows.isEmpty() ? null : rows.getFirst());
        Ui.ListView list = Ui.list("paintings").items(rows, r -> Ui.listItem(r.id().toString(), r.motif()).icon(Ui.item(Material.PAINTING))
                        .subtitle(ctx.text(r.loaded() ? "exhibitions.loaded" : "exhibitions.unloaded")))
                .empty(ctx.text("panel.empty")).selected(chosen == null ? null : chosen.id().toString()).onSelect(select).grow(2);
        Ui.Box detail = Ui.col().gap(6).grow(3);
        if (chosen != null) {
            detail.children(Ui.heading(chosen.motif()), Ui.kv().id("painting-stats").row(ctx.text("panel.location"), chosen.location()),
                    Ui.badge(ctx.text(chosen.loaded() ? "exhibitions.loaded" : "exhibitions.unloaded")));
            if (o.owner()) detail.child(Ui.button("remove").label(ctx.text("panel.remove")).icon(Ui.item(Material.BARRIER)).danger()
                    .confirm(ctx.text("panel.remove"), ctx.text("panel.remove-confirm"))
                    .onClick(a -> MotifPanel.refresh(a, actions.change(a.player(), e.id(), "remove", chosen.id().toString()))));
        }
        Ui.Tabs tabs = Ui.tabs("exhibition-tabs").tab("paintings", ctx.text("panel.paintings"), Ui.row().gap(8).children(list, detail));
        if (o.owner()) {
            Ui.Box tools = Ui.col().gap(8).children(
                    Ui.button("add").label(ctx.text("exhibitions.add")).icon(Ui.item(Material.PAINTING))
                            .onClick(a -> MotifPanel.refresh(a, actions.change(a.player(), e.id(), "add", ""))),
                    edit(ctx, actions, e, "title", "exhibitions.title-prompt", e.title(), 64),
                    edit(ctx, actions, e, "description", "exhibitions.description-prompt", e.description(), 240),
                    Ui.toggle("published", ctx.text("exhibitions.publish")).value(e.published())
                            .onChange((a, value) -> MotifPanel.refresh(a, actions.change(a.player(), e.id(), "publish", value.toString()))),
                    Ui.button("delete").label(ctx.text("exhibitions.delete")).icon(Ui.item(Material.BARRIER)).danger()
                            .confirm(ctx.text("exhibitions.delete"), ctx.text("exhibitions.delete-confirm"))
                            .onClick(a -> {
                                Result result = actions.change(a.player(), e.id(), "delete", "");
                                if (result.success()) a.closeAndThen(p -> actions.open(p, null));
                                return result;
                            }));
            tabs.tab("manage", ctx.text("panel.settings"), tools);
        }
        Panel panel = Panel.page(e.title()).subtitle(e.description()).icon(Ui.item(Material.PAINTING)).size(Panel.Size.LARGE)
                .children(status(ctx, e), stats(ctx, e), tabs);
        if (!o.owner()) panel.child(Ui.toggle("vote", ctx.text("exhibitions.vote")).value(o.voted())
                .onChange((a, value) -> MotifPanel.refresh(a, actions.change(a.player(), e.id(), "vote", value.toString()))));
        panel.footer(Ui.button("back").label(ctx.text("exhibitions.back")).icon(Ui.item(Material.BOOK)).ghost()
                .onClick(a -> { a.closeAndThen(p -> actions.open(p, null)); return Result.ok(); }));
        if (o.tour()) panel.footer(Ui.button("tour").label(ctx.text("exhibitions.tour")).icon(Ui.item(Material.COMPASS)).primary()
                .onClick(a -> { a.closeAndThen(p -> actions.tour(p, e.id())); return Result.ok(); }));
        return footer(ctx, panel, page, pages, turn);
    }
    private static Ui.Form edit(PanelContext ctx, Actions actions, Entry e, String action, String label, String value, int max) {
        return Ui.form("edit-" + action, ctx.text("panel.save"))
                .child(Ui.input(action, ctx.text(label)).value(value).maxLength(max))
                .onSubmit((a, values) -> MotifPanel.refresh(a, actions.change(a.player(), e.id(), action, value(values, action))));
    }
    private static String value(Map<String, Object> values, String key) { return values.get(key) instanceof String text ? text : ""; }
    private static Ui.Badge status(PanelContext ctx, Entry e) { return Ui.badge(ctx.text(e.published() ? "exhibitions.public" : "exhibitions.private")); }
    private static Ui.Kv stats(PanelContext ctx, Entry e) { return Ui.kv().id("exhibition-stats").row(ctx.text("panel.paintings"), String.valueOf(e.paintings())).row(ctx.text("panel.votes"), String.valueOf(e.votes())); }
    private static int pages(int count) { return Math.max(1, (count + PAGE_SIZE - 1) / PAGE_SIZE); }
    private static <T> List<T> slice(List<T> rows, int page) { return rows.subList(page * PAGE_SIZE, Math.min(rows.size(), (page + 1) * PAGE_SIZE)); }
    private static Panel footer(PanelContext ctx, Panel panel, int page, int pages, Ui.Change<Integer> turn) {
        return panel.footer(Ui.button("previous").label(ctx.text("exhibitions.previous")).icon(Ui.item(Material.ARROW)).ghost().disabled(page == 0).onClick(a -> turn.handle(a, page - 1)),
                Ui.button("next").label(ctx.text("exhibitions.next")).icon(Ui.item(Material.ARROW)).ghost().disabled(page + 1 >= pages).onClick(a -> turn.handle(a, page + 1)));
    }
}
