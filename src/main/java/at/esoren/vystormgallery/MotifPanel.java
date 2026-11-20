package at.esoren.vystormgallery;

import Vystorm.vystorm_core.panel.*;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Pure motif presentation; painting access and mutations remain in GalleryPlugin's services. */
final class MotifPanel {
    static final String NAME = "motifs";
    static final int PAGE_SIZE = 9;
    record Row(String key, Object title, String size, boolean favourite) {}
    record Overview(UUID painting, List<Row> rows, String current, boolean favourites, boolean lock,
                    boolean locked, String owner, boolean slideshow, int seconds, int minSeconds, int maxSeconds, int defaultSeconds) {
        Overview { rows = List.copyOf(rows); }
    }
    interface Actions {
        boolean allowed(Player player, UUID painting);
        void choose(Player player, UUID painting, String key);
        Result favourite(Player player, UUID painting, String key, boolean value);
        Result lock(Player player, UUID painting, boolean value);
        Result slideshow(Player player, UUID painting, boolean value);
        Result interval(Player player, UUID painting);
        Result seconds(Player player, UUID painting, int value);
    }
    private final Map<UUID, String> selected = new HashMap<>();
    private final Map<UUID, Integer> pages = new HashMap<>();

    void forget(UUID player) { selected.remove(player); pages.remove(player); }
    Panel render(PanelContext ctx, Overview o, Actions actions) {
        UUID player = ctx.player().getUniqueId();
        String choice = selected.getOrDefault(player, o.current());
        int initial = 0;
        for (int i = 0; i < o.rows().size(); i++) if (o.rows().get(i).key().equals(choice)) initial = i / PAGE_SIZE;
        return panel(ctx, o, choice, pages.getOrDefault(player, initial), actions,
                (a, key) -> {
                    if (!actions.allowed(a.player(), o.painting()) || o.rows().stream().noneMatch(r -> r.key().equals(key))) return Result.error(ctx.text("denied"));
                    selected.put(a.player().getUniqueId(), key); a.refresh(); return Result.ok();
                }, (a, page) -> {
                    if (!actions.allowed(a.player(), o.painting())) return Result.error(ctx.text("denied"));
                    pages.put(a.player().getUniqueId(), page); selected.remove(a.player().getUniqueId()); a.refresh(); return Result.ok();
                });
    }

    static Panel panel(PanelContext ctx, Overview o, String selected, int requestedPage, Actions actions,
                       Ui.Change<String> select, Ui.Change<Integer> turn) {
        int pages = Math.max(1, (o.rows().size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.clamp(requestedPage, 0, pages - 1);
        List<Row> rows = o.rows().subList(page * PAGE_SIZE, Math.min(o.rows().size(), (page + 1) * PAGE_SIZE));
        Row chosen = rows.stream().filter(r -> r.key().equals(selected)).findFirst().orElse(rows.isEmpty() ? null : rows.getFirst());
        Ui.ListView list = Ui.list("motifs").items(rows, r -> Ui.listItem(r.key(), r.title())
                .icon(Ui.item(Material.PAINTING)).right(r.size()).subtitle(r.favourite() ? ctx.text("panel.favourite") : ""))
                .empty(ctx.text("no-options")).selected(chosen == null ? null : chosen.key()).onSelect(select).grow(2);
        Ui.Box detail = Ui.col().gap(6).grow(3);
        if (chosen != null) {
            detail.children(Ui.heading(chosen.title()), Ui.kv().id("motif-stats").row(ctx.text("panel.size"), chosen.size()));
            if (chosen.key().equals(o.current())) detail.child(Ui.badge(ctx.text("panel.current")).id("current"));
            detail.child(Ui.button("apply").label(ctx.text("panel.apply")).icon(Ui.item(Material.PAINTING)).primary()
                    .onClick(a -> { a.closeAndThen(p -> actions.choose(p, o.painting(), chosen.key())); return Result.ok(); }));
            if (o.favourites()) detail.child(Ui.toggle("favourite", ctx.text("panel.favourite")).value(chosen.favourite())
                    .onChange((a, value) -> refresh(a, actions.favourite(a.player(), o.painting(), chosen.key(), value))));
        }
        Ui.Tabs tabs = Ui.tabs("motif-tabs").tab("motifs", ctx.text("panel.motifs"), Ui.row().gap(8).children(list, detail));
        if (o.lock() || o.slideshow()) {
            // settings of this painting: one row each on Vystorm Screens (Core 0.66.0 list layout)
            Ui.Section settings = Ui.section(ctx.text("panel.settings-title")).list();
            if (o.lock()) settings.child(Ui.toggle("lock", ctx.text("panel.lock-name")).value(o.locked())
                    .description(o.locked() ? ctx.text("menu-locked", "owner", o.owner()) : ctx.text("panel.lock-hint"))
                    .onChange((a, value) -> refresh(a, actions.lock(a.player(), o.painting(), value))));
            if (o.slideshow()) settings.children(
                    Ui.toggle("slideshow", ctx.text("panel.slideshow-name")).value(o.seconds() > 0)
                            .description(ctx.text("panel.slideshow-desc"))
                            .onChange((a, value) -> refresh(a, actions.slideshow(a.player(), o.painting(), value))),
                    Ui.number("interval", ctx.text("panel.interval")).range(o.minSeconds(), o.maxSeconds(), 1)
                            .value(o.seconds() > 0 ? o.seconds() : o.defaultSeconds()).decimals(0).disabled(o.seconds() == 0)
                            .tooltip(ctx.text("panel.interval-desc"))
                            .onChange((a, value) -> refresh(a, actions.seconds(a.player(), o.painting(), value.intValue()))),
                    Ui.button("next-interval").label(ctx.text("panel.next-interval")).icon(Ui.item(Material.CLOCK))
                            .tooltip(ctx.text("panel.next-interval-desc"))
                            .onClick(a -> refresh(a, actions.interval(a.player(), o.painting()))));
            tabs.tab("settings", ctx.text("panel.settings"), settings);
        }
        return Panel.page(ctx.text("menu-title", "count", o.rows().size())).subtitle(ctx.text("panel.pick-motif"))
                .icon(Ui.item(Material.PAINTING)).size(Panel.Size.LARGE).child(tabs)
                .footer(Ui.button("previous").label(ctx.text("menu-previous", "page", Math.max(1, page), "pages", pages)).icon(Ui.item(Material.ARROW)).ghost()
                                .disabled(page == 0).onClick(a -> turn.handle(a, page - 1)),
                        Ui.button("next").label(ctx.text("menu-next", "page", Math.min(pages, page + 2), "pages", pages)).icon(Ui.item(Material.ARROW)).ghost()
                                .disabled(page + 1 >= pages).onClick(a -> turn.handle(a, page + 1)));
    }

    static Result refresh(PanelAction action, Result result) { if (result.success()) action.refresh(); return result; }
}
