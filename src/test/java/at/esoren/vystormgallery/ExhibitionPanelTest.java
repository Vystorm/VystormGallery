package at.esoren.vystormgallery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import Vystorm.vystorm_core.panel.*;
import com.google.gson.JsonObject;
import java.util.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class ExhibitionPanelTest {
    private final ExhibitionPanel.Actions actions = mock(ExhibitionPanel.Actions.class);
    private ExhibitionPanel.Entry entry(int i) { return new ExhibitionPanel.Entry(new UUID(0, i), "Gallery " + i, "Paintings by the river", 2, 7, true); }
    private JsonObject browser(List<ExhibitionPanel.Entry> entries, int page) throws Exception {
        return PanelTesting.render(ExhibitionPanel.browser(PanelTestSupport.context(mock(Player.class), "en"), entries, null, page,
                actions, (a, v) -> Result.ok(), (a, v) -> Result.ok()));
    }
    private JsonObject detail(boolean owner, boolean tour, String code) throws Exception {
        UUID first = new UUID(1, 1), second = new UUID(1, 2);
        var o = new ExhibitionPanel.Overview(entry(1), List.of(
                new ExhibitionPanel.PaintingRow(first, "minecraft:kebab", "world 10, 64, 20", true),
                new ExhibitionPanel.PaintingRow(second, "custom:river", "world 100, 64, 200", false)), owner, true, tour);
        return PanelTesting.render(ExhibitionPanel.detail(PanelTestSupport.context(mock(Player.class), code), o, second.toString(), 0,
                actions, (a, v) -> Result.ok(), (a, v) -> Result.ok()));
    }
    @Test void givenBrowser_whenRendered_thenSelectionDetailsAndCreateFormAreShown() throws Exception {
        var doc = browser(List.of(entry(1), entry(2)), 0);
        assertTrue(PanelTesting.ids(doc).containsAll(List.of("exhibitions", "exhibition-stats", "open", "create", "name")));
        assertTrue(doc.toString().contains("Gallery 1") && doc.toString().contains("2 paintings") && doc.toString().contains("Published"));
    }
    @Test void givenManyExhibitions_whenLastPageRequested_thenOnlyNineEntriesPerPageAndBoundedNavigation() throws Exception {
        List<ExhibitionPanel.Entry> entries = new ArrayList<>();
        for (int i = 0; i < 20; i++) entries.add(entry(i));
        var doc = browser(entries, 1);
        assertEquals(9, PanelTesting.find(doc, "exhibitions").getAsJsonArray("items").size());
        doc = browser(entries, 100);
        assertEquals(2, PanelTesting.find(doc, "exhibitions").getAsJsonArray("items").size());
        assertTrue(PanelTesting.find(doc, "next").get("disabled").getAsBoolean());
    }
    @Test void givenEmptyBrowser_whenRendered_thenCreationRemainsAvailable() throws Exception {
        var doc = browser(List.of(), 4);
        assertNotNull(PanelTesting.find(doc, "create")); assertNull(PanelTesting.find(doc, "open"));
        assertTrue(doc.toString().contains("Nothing here yet."));
    }
    @Test void givenOwner_whenRendered_thenManagementAndDestructiveConfirmationsArePresent() throws Exception {
        var doc = detail(true, true, "en");
        assertTrue(PanelTesting.ids(doc).containsAll(List.of("add", "edit-title", "edit-description", "published", "delete", "remove", "tour", "back")));
        assertNotNull(PanelTesting.find(doc, "delete").get("confirm"));
        assertNotNull(PanelTesting.find(doc, "remove").get("confirm"));
        assertNull(PanelTesting.find(doc, "vote"));
        assertTrue(doc.toString().contains("world 100, 64, 200") && doc.toString().contains("unloaded"));
    }
    @Test void givenVisitorWithoutTour_whenRendered_thenOnlyVisitorControlsExist() throws Exception {
        var doc = detail(false, false, "en");
        for (String id : List.of("add", "delete", "remove", "published", "edit-title", "edit-description", "tour")) assertNull(PanelTesting.find(doc, id), id);
        assertTrue(PanelTesting.find(doc, "vote").get("value").getAsBoolean());
    }
    @Test void givenAllLanguages_whenRendered_thenManagementLabelsAreLocalizedAndSchemaIsValid() throws Exception {
        for (String code : PanelTestSupport.LANGUAGES) {
            var doc = detail(true, true, code);
            String label = PanelTesting.plain(PanelTesting.find(doc, "remove").get("label"));
            assertEquals(PanelTestSupport.context(mock(Player.class), code).text("panel.remove"), label);
            if (!code.equals("en")) assertNotEquals("Remove painting", label);
            assertFalse(doc.toString().contains("panel."), doc.toString());
        }
    }
}
