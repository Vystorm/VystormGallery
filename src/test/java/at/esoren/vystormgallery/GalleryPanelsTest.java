package at.esoren.vystormgallery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import Vystorm.vystorm_core.lang.Lang;
import Vystorm.vystorm_core.panel.*;
import java.util.*;
import java.util.logging.Logger;
import org.bukkit.entity.*;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

class GalleryPanelsTest {
    GalleryPlugin plugin;
    Panels panels;
    GalleryPanels integration;
    Exhibitions exhibitions;
    ExhibitionStore store;
    Player player;
    UUID id;
    MockedStatic<Panels> api;
    Map<String, PanelSpec> specs;
    @BeforeEach void setup() {
        plugin = mock(GalleryPlugin.class); panels = mock(Panels.class); exhibitions = mock(Exhibitions.class); store = mock(ExhibitionStore.class);
        player = mock(Player.class); id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(UUID.randomUUID()); when(player.isOnline()).thenReturn(true);
        when(player.hasPermission(anyString())).thenReturn(true); when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        Lang lang = mock(Lang.class); when(plugin.lang()).thenReturn(lang);
        when(lang.ref(anyString())).thenAnswer(i -> i.getArgument(0));
        when(lang.plain(eq(player), anyString())).thenAnswer(i -> i.getArgument(1));
        specs = new HashMap<>();
        doAnswer(i -> { specs.put(i.getArgument(0), i.getArgument(1)); return null; }).when(panels).register(anyString(), any());
        api = mockStatic(Panels.class); api.when(() -> Panels.of(plugin)).thenReturn(panels);
        integration = GalleryPanels.register(plugin, TestCore.api(), exhibitions, store);
    }
    @AfterEach void cleanup() { api.close(); }
    private ExhibitionStore.Exhibition exhibition(UUID owner, boolean published) {
        return new ExhibitionStore.Exhibition(id, owner, "show", "Show", "", published,
                List.of(new ExhibitionStore.PaintingRef(UUID.randomUUID(), UUID.randomUUID(), 0, 64, 0, "minecraft:kebab")), Set.of());
    }
    @Test void givenRegistration_whenInspected_thenThreePanelsHaveFallbacksAndOnlyBrowserHasMenuEntry() {
        assertEquals(Set.of("motifs", "exhibition", "exhibitions"), specs.keySet());
        specs.values().forEach(spec -> { assertNotNull(spec.fallback()); assertTrue(spec.access().test(player)); });
        assertNotNull(specs.get("exhibitions").menuCategory());
        assertNull(specs.get("exhibition").menuCategory()); assertNull(specs.get("motifs").menuCategory());
        when(player.hasPermission("vystormgallery.use")).thenReturn(false);
        specs.values().forEach(spec -> assertFalse(spec.access().test(player)));
    }
    @Test void givenNoNativePanel_whenOpened_thenOriginalChestFallbackKeepsItsTargetAndPage() {
        var owned = exhibition(player.getUniqueId(), false);
        when(store.get(id)).thenReturn(owned);
        when(panels.open(eq(player), anyString())).thenAnswer(i -> {
            specs.get(i.<String>getArgument(1)).fallback().accept(player); return PanelOpenResult.FALLBACK;
        });
        assertTrue(integration.openExhibitions(player, id, 3)); verify(exhibitions).openChest(player, id, 3);
        assertTrue(integration.openExhibitions(player, null, 2)); verify(exhibitions).listChest(player, 2);
        Painting painting = mock(Painting.class); when(painting.getUniqueId()).thenReturn(id); when(plugin.panelPainting(player, id)).thenReturn(painting);
        assertTrue(integration.openMotifs(player, painting, 1)); verify(plugin).openChestMenu(player, painting, 1);
    }
    @Test void givenOpeningFailure_whenMotifOpenerRuns_thenChestStillOpens() throws Exception {
        Painting painting = mock(Painting.class); when(painting.getUniqueId()).thenReturn(id); when(plugin.panelPainting(player, id)).thenReturn(painting);
        GallerySafetyTest.field(plugin, "galleryPanels", integration);
        doCallRealMethod().when(plugin).openMenu(player, painting, -1);
        when(panels.open(player, "motifs")).thenThrow(new IllegalStateException("panel failure"));
        plugin.openMenu(player, painting, -1); verify(plugin).openChestMenu(player, painting, -1);
        when(panels.open(player, "exhibitions")).thenReturn(PanelOpenResult.NONE);
        assertFalse(integration.openExhibitions(player, null, 0));
    }
    @Test void givenRevokedAccessOrHiddenExhibition_whenActionsArrive_thenStorageIsUntouched() {
        when(store.get(id)).thenReturn(exhibition(UUID.randomUUID(), false));
        assertFalse(integration.exhibitionActions.change(player, id, "delete", "").success());
        var owned = exhibition(player.getUniqueId(), true);
        when(store.get(id)).thenReturn(owned);
        when(player.hasPermission("vystormgallery.use")).thenReturn(false);
        assertFalse(integration.exhibitionActions.change(player, id, "publish", "false").success());
        verify(store, never()).delete(any(), any()); verify(store, never()).publish(any(), any(), anyBoolean());
    }
    @Test void givenVisitor_whenOwnerActionsArrive_thenTheyAreDeniedEvenIfExhibitionIsPublic() {
        when(store.get(id)).thenReturn(exhibition(UUID.randomUUID(), true));
        for (String action : List.of("delete", "title", "description", "add", "remove", "publish"))
            assertFalse(integration.exhibitionActions.change(player, id, action, "value").success(), action);
        verify(store, never()).describe(any(), any(), any(), anyBoolean()); verify(exhibitions, never()).addPainting(any(), any());
    }
    @Test void givenOwnerAndFreshState_whenEditingOrRemoving_thenExistingStoreMethodsAreUsed() {
        var e = exhibition(player.getUniqueId(), true); when(store.get(id)).thenReturn(e);
        when(store.describe(player.getUniqueId(), id, "New title", true)).thenReturn(ExhibitionStore.Result.SAVED);
        assertTrue(integration.exhibitionActions.change(player, id, "title", "New title").success());
        verify(store).describe(player.getUniqueId(), id, "New title", true);
        assertFalse(integration.exhibitionActions.change(player, id, "remove", UUID.randomUUID().toString()).success());
        verify(store, never()).painting(any(), any(), any(), anyBoolean());
        var ref = e.paintings().getFirst();
        when(store.painting(player.getUniqueId(), id, ref, false)).thenReturn(ExhibitionStore.Result.SAVED);
        assertTrue(integration.exhibitionActions.change(player, id, "remove", ref.entity().toString()).success());
    }
    @Test void givenLostPaintingOrFeaturePermission_whenControlsArrive_thenServicesAreNotCalled() {
        Slideshow slideshow = mock(Slideshow.class); PaintingLock lock = mock(PaintingLock.class);
        when(plugin.slideshow()).thenReturn(slideshow); when(plugin.lock()).thenReturn(lock);
        assertFalse(integration.motifActions.slideshow(player, id, true).success());
        assertFalse(integration.motifActions.lock(player, id, true).success());
        verifyNoInteractions(slideshow, lock);
        Painting painting = mock(Painting.class); when(plugin.panelPainting(player, id)).thenReturn(painting);
        when(plugin.slideshowEnabled()).thenReturn(true); when(player.hasPermission("vystormgallery.slideshow")).thenReturn(false);
        assertFalse(integration.motifActions.interval(player, id).success()); verifyNoInteractions(slideshow);
    }
    @Test void givenQuit_whenForgotten_thenOldTargetsCannotOpenFallbackChests() {
        var owned = exhibition(player.getUniqueId(), true);
        when(store.get(id)).thenReturn(owned);
        when(panels.open(player, "exhibition")).thenReturn(PanelOpenResult.NATIVE);
        assertTrue(integration.openExhibitions(player, id, 0));
        integration.forget(player.getUniqueId()); specs.get("exhibition").fallback().accept(player);
        verify(exhibitions, never()).openChest(any(), any(), anyInt());
    }
    @Test void givenStorageFailure_whenEditing_thenErrorIsReturnedWithoutClaimingSuccess() {
        var owned = exhibition(player.getUniqueId(), true); when(store.get(id)).thenReturn(owned);
        when(store.delete(player.getUniqueId(), id)).thenReturn(ExhibitionStore.Result.UNAVAILABLE);
        assertFalse(integration.exhibitionActions.change(player, id, "delete", "").success());
        verify(exhibitions, never()).list(any(), anyInt());
    }
    @Test void givenPartialRegistrationFailure_whenRegistering_thenCoreRegistrationsAreReleased() {
        doThrow(new IllegalStateException("panel registration failed")).when(panels).register(eq("exhibition"), any());
        assertThrows(IllegalStateException.class, () -> GalleryPanels.register(plugin, TestCore.api(), exhibitions, store));
        api.verify(() -> Panels.release(plugin));
    }
    @Test void givenChangedSlideshowLimits_whenAmountArrives_thenCurrentBoundsAndExistingServiceAreUsed() {
        Painting painting = mock(Painting.class); when(plugin.panelPainting(player, id)).thenReturn(painting);
        Slideshow slideshow = mock(Slideshow.class); PaintingLock lock = mock(PaintingLock.class);
        when(plugin.slideshow()).thenReturn(slideshow); when(plugin.lock()).thenReturn(lock);
        when(plugin.slideshowEnabled()).thenReturn(true); when(lock.canEdit(player, painting)).thenReturn(true);
        when(slideshow.minSeconds()).thenReturn(10); when(slideshow.maxSeconds()).thenReturn(60);
        when(slideshow.seconds(painting)).thenReturn(30, 60);
        assertTrue(integration.motifActions.seconds(player, id, 3600).success());
        verify(slideshow).command(player, painting, "60", "vgallery");
        when(lock.canEdit(player, painting)).thenReturn(false);
        assertFalse(integration.motifActions.seconds(player, id, 20).success());
        verify(slideshow, never()).command(player, painting, "20", "vgallery");
    }
}
