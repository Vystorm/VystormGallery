package at.esoren.vystormgallery;

import Vystorm.vystorm_core._advancedMethods.placeholderHandler.PlaceholderInterface;
import Vystorm.vystorm_core._advancedMethods.placeholderHandler.PlaceholderNamespace;
import java.util.Collection;
import java.util.List;
import java.util.function.UnaryOperator;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * The values of Vystorm Gallery in Core's placeholder registry, namespace {@code gallery}. The album lives on the
 * player and exhibitions in a plain map, so every value is answered on the server thread only. A value of a
 * switched-off feature is empty.
 *
 * <pre>
 * %vystorm_gallery_album_found%           {gallery:album_found}            motifs the player discovered
 * %vystorm_gallery_album_total%           {gallery:album_total}            all motifs
 * %vystorm_gallery_album_groups_complete%, _album_groups_rewarded, _exhibitions_owned, _exhibition_votes,
 * %vystorm_gallery_exhibitions_published%, %vystorm_gallery_touring% (true or false)
 * </pre>
 */
final class GalleryPlaceholders {
    static final String NAMESPACE = "gallery";

    /** What the values read, at call time. */
    interface Source {
        boolean on(Feature feature);
        MotifAlbum album(Player player);
        List<Cycle.Variant> motifs();
        Collection<ExhibitionStore.Exhibition> exhibitions();
        boolean touring(Player player);
    }

    private GalleryPlaceholders() {}

    static PlaceholderNamespace register(PlaceholderInterface registry, Plugin owner, Source source, UnaryOperator<String> describe) {
        PlaceholderNamespace ns = registry.register(owner, NAMESPACE);
        ns.player("album_found", describe.apply("placeholder.album_found"), player -> source.on(Feature.ALBUM) ? source.album(player).count() : "");
        ns.global("album_total", describe.apply("placeholder.album_total"), () -> source.on(Feature.ALBUM) ? source.motifs().size() : "");
        ns.player("album_groups_complete", describe.apply("placeholder.album_groups_complete"), player -> source.on(Feature.ALBUM)
                ? source.album(player).groups(source.motifs()).stream().filter(MotifAlbum.Group::complete).count() : "");
        ns.player("album_groups_rewarded", describe.apply("placeholder.album_groups_rewarded"), player -> source.on(Feature.ALBUM)
                ? source.album(player).groups(source.motifs()).stream().filter(MotifAlbum.Group::rewarded).count() : "");
        ns.value("exhibitions_owned", describe.apply("placeholder.exhibitions_owned"), viewer -> source.on(Feature.EXHIBITIONS)
                ? source.exhibitions().stream().filter(e -> e.owner().equals(viewer.getUniqueId())).count() : "");
        ns.value("exhibition_votes", describe.apply("placeholder.exhibition_votes"), viewer -> source.on(Feature.EXHIBITIONS)
                ? source.exhibitions().stream().filter(e -> e.owner().equals(viewer.getUniqueId())).mapToInt(e -> e.votes().size()).sum() : "");
        ns.global("exhibitions_published", describe.apply("placeholder.exhibitions_published"), () -> source.on(Feature.EXHIBITIONS)
                ? source.exhibitions().stream().filter(ExhibitionStore.Exhibition::published).count() : "");
        ns.player("touring", describe.apply("placeholder.touring"), player -> source.on(Feature.EXHIBITIONS) && source.on(Feature.TOURS)
                ? source.touring(player) : "");
        return ns;
    }
}
