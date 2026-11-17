package at.esoren.vystormgallery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import Vystorm.vystorm_core.panel.*;
import com.google.gson.JsonObject;
import java.util.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class MotifPanelTest {
    private MotifPanel.Overview overview(boolean controls) {
        return new MotifPanel.Overview(UUID.randomUUID(), List.of(
                new MotifPanel.Row("minecraft:kebab", "Kebab", "1×1", false),
                new MotifPanel.Row("minecraft:pool", "Pool", "2×1", true)), "minecraft:pool", controls, controls, true, "Alex", controls, 30, 5, 3600, 30);
    }
    private JsonObject write(MotifPanel.Overview overview, String selected, int page, String language) throws Exception {
        return PanelTesting.render(MotifPanel.panel(PanelTestSupport.context(mock(Player.class), language), overview, selected, page,
                mock(MotifPanel.Actions.class), (a, v) -> Result.ok(), (a, v) -> Result.ok()));
    }
    @Test void givenSelectedMotif_whenRendered_thenDetailsCurrentStateAndControlsAreShown() throws Exception {
        JsonObject doc = write(overview(true), "minecraft:pool", 0, "en");
        assertTrue(PanelTesting.ids(doc).containsAll(List.of("motifs", "motif-stats", "current", "apply", "favourite", "lock", "slideshow", "interval")));
        assertEquals("minecraft:pool", PanelTesting.find(doc, "motifs").get("selected").getAsString());
        assertTrue(doc.toString().contains("2×1") && doc.toString().contains("Alex"));
        assertEquals("Hang this motif", PanelTesting.plain(PanelTesting.find(doc, "apply").get("label")));
        assertTrue(PanelTesting.find(doc, "favourite").get("value").getAsBoolean());
        JsonObject interval = PanelTesting.find(doc, "interval");
        assertEquals(5, interval.get("min").getAsInt()); assertEquals(3600, interval.get("max").getAsInt());
        assertEquals(30, interval.get("value").getAsInt());
    }
    @Test void givenUnavailableFeatures_whenRendered_thenTheirControlsAreAbsent() throws Exception {
        var ids = PanelTesting.ids(write(overview(false), null, 0, "en"));
        for (String id : List.of("favourite", "lock", "slideshow", "interval")) assertFalse(ids.contains(id));
        assertTrue(ids.contains("apply"));
    }
    @Test void givenManyMotifsAndStaleSelection_whenRendered_thenPageIsBoundedAndSelectionRecovers() throws Exception {
        List<MotifPanel.Row> rows = new ArrayList<>();
        for (int i = 0; i < 23; i++) rows.add(new MotifPanel.Row("custom:motif_" + i, "Motif " + i, "1×1", false));
        var o = new MotifPanel.Overview(UUID.randomUUID(), rows, "custom:motif_0", false, false, false, "", false, 0, 5, 3600, 30);
        var doc = write(o, "removed:key", 100, "en");
        assertEquals(5, PanelTesting.find(doc, "motifs").getAsJsonArray("items").size());
        assertEquals("custom:motif_18", PanelTesting.find(doc, "motifs").get("selected").getAsString());
        assertTrue(PanelTesting.find(doc, "next").get("disabled").getAsBoolean());
        assertFalse(PanelTesting.find(doc, "previous").get("disabled").getAsBoolean());
    }
    @Test void givenNoMotifs_whenRendered_thenNoApplyActionExists() throws Exception {
        var o = new MotifPanel.Overview(UUID.randomUUID(), List.of(), "missing:key", false, false, false, "", false, 0, 5, 3600, 30);
        var doc = write(o, null, -20, "en");
        assertNull(PanelTesting.find(doc, "apply"));
        assertTrue(doc.toString().contains("No other motif fits here."));
    }
    @Test void givenAllLanguages_whenRendered_thenLabelsAreTranslatedAndSchemaIsValid() throws Exception {
        for (String code : PanelTestSupport.LANGUAGES) {
            var ctx = PanelTestSupport.context(mock(Player.class), code);
            var doc = write(overview(true), "minecraft:pool", 0, code);
            String label = PanelTesting.plain(PanelTesting.find(doc, "apply").get("label"));
            assertEquals(ctx.text("panel.apply"), label);
            if (!code.equals("en")) assertNotEquals("Hang this motif", label);
            assertFalse(doc.toString().contains("panel."), doc.toString());
        }
    }
}
