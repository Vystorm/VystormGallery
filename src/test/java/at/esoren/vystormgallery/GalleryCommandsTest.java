package at.esoren.vystormgallery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import Vystorm.vystorm_core.commands.CommandRegistration;
import Vystorm.vystorm_core.commands.CommandSpec;
import Vystorm.vystorm_core.commands.CommandTesting;
import Vystorm.vystorm_core.lang.Lang;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

/** /vgallery through Core's command tree with the real language files: names, routing, completion and help per language. */
class GalleryCommandsTest {
    private static final String[] LANGUAGES = {"en", "de", "zh_cn"};
    private final List<String> chat = new ArrayList<>();
    private final GalleryPlugin plugin = mock(GalleryPlugin.class);

    private static String read(String name) throws Exception {
        try (InputStream in = GalleryCommandsTest.class.getResourceAsStream("/lang/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private CommandRegistration registration(GalleryPlugin handlers) throws Exception {
        Plugin owner = mock(Plugin.class);
        when(owner.isEnabled()).thenReturn(true);
        when(owner.getName()).thenReturn("VystormGallery");
        Lang lang = Lang.create("VystormGallery", Map.of("en", read("en.yml"), "de", read("de.yml"), "zh_cn", read("zh_cn.yml")));
        return CommandTesting.detached(owner, lang, GalleryPlugin.commandSpec(handlers));
    }

    private Player player(Locale locale) {
        Player p = mock(Player.class);
        when(p.locale()).thenReturn(locale);
        when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        when(p.hasPermission(anyString())).thenAnswer(i -> !((String) i.getArgument(0)).endsWith(".admin"));
        doAnswer(i -> { chat.add(PlainTextComponentSerializer.plainText().serialize((Component) i.getArgument(0))); return null; })
                .when(p).sendMessage(any(Component.class));
        return p;
    }

    @Test
    void givenLanguages_whenCompleting_thenSubcommandsInPlayersLanguage() throws Exception {
        CommandRegistration r = registration(null);
        String[] none = {""};
        assertEquals(List.of("exhibitions", "album", "menu", "random", "undo", "lock", "slideshow", "playlist", "help"), r.suggest(player(Locale.US), "vgallery", none));
        assertEquals(List.of("exhibitions", "album", "menü", "zufall", "rückgängig", "sperren", "diashow", "motivliste", "hilfe"), r.suggest(player(Locale.GERMANY), "gemaelde", none));
        assertEquals(List.of("exhibitions", "album", "菜单", "随机", "撤销", "锁定", "幻灯片", "播放列表", "帮助"), r.suggest(player(Locale.SIMPLIFIED_CHINESE), "画廊", none));
        assertEquals(List.of("aus", "10", "30", "60", "5m"), r.suggest(player(Locale.GERMANY), "gemaelde", new String[]{"diashow", ""}));
    }

    @Test
    void givenExhibitionsWord_whenCompleting_thenActionsFilteredByPrefix() throws Exception {
        CommandRegistration r = registration(null);
        assertEquals(Exhibitions.ACTIONS, r.suggest(player(Locale.US), "vgallery", new String[]{"exhibitions", ""}));
        assertEquals(List.of("publish"), r.suggest(player(Locale.US), "vgallery", new String[]{"exhibitions", "pu"}));
        assertEquals(List.of("description", "delete"), r.suggest(player(Locale.US), "vgallery", new String[]{"exhibitions", "DE"}));
        assertEquals(List.of(), Exhibitions.suggestActions("open "));
    }

    @Test
    void playlistActionsRouteInEveryLanguage() throws Exception {
        CommandRegistration r = registration(plugin);
        String[] roots = {"playlist", "motivliste", "播放列表"};
        String[][] actions = {{"add", "remove", "first", "last", "clear", "list"}, {"hinzufügen", "entfernen", "anfang", "ende", "leeren", "anzeigen"}, {"添加", "移除", "置顶", "置底", "清空", "查看"}};
        String[] ids = {"add", "remove", "first", "last", "clear", "list"};
        Player p = player(Locale.US);
        for (int language = 0; language < roots.length; language++) {
            clearInvocations(plugin);
            r.execute(p, "vgallery", new String[]{roots[language]});
            verify(plugin).handle(p, "vgallery", "playlist", "list");
            for (int i = 0; i < ids.length; i++) {
                clearInvocations(plugin);
                r.execute(p, "vgallery", new String[]{roots[language], actions[language][i]});
                verify(plugin).handle(p, "vgallery", "playlist", ids[i]);
            }
        }
        assertTrue(chat.isEmpty(), chat.toString());
    }

    @Test
    void givenEveryOldEnglishPath_whenRun_thenSameEnglishArgumentsReachTheOldLogic() throws Exception {
        CommandRegistration r = registration(plugin);
        Player p = player(Locale.GERMANY);
        String[][] paths = {{}, {"menu"}, {"random"}, {"undo"}, {"lock"}, {"slideshow"}, {"slideshow", "30"}, {"slideshow", "5m"},
                {"slideshow", "off"}, {"slideshow", "stop"}, {"kebab"}, {"minecraft:aztec"}, {"MENU"}};
        String[][] expected = {{}, {"menu"}, {"random"}, {"undo"}, {"lock"}, {"slideshow"}, {"slideshow", "30"}, {"slideshow", "5m"},
                {"slideshow", "off"}, {"slideshow", "stop"}, {"kebab"}, {"minecraft:aztec"}, {"menu"}};
        for (int i = 0; i < paths.length; i++) {
            clearInvocations(plugin);
            r.execute(p, "vgallery", paths[i]);
            verify(plugin).handle(p, "vgallery", expected[i]);
        }
        assertEquals(List.of(), chat, "no error messages");
    }

    @Test
    void givenLocalizedNames_whenRun_thenEnglishIds() throws Exception {
        CommandRegistration r = registration(plugin);
        Player de = player(Locale.GERMANY);
        r.execute(de, "gemaelde", new String[]{"diashow", "aus"});
        verify(plugin).handle(de, "gemaelde", "slideshow", "off");
        r.execute(de, "gemaelde", new String[]{"zufall"});
        verify(plugin).handle(de, "gemaelde", "random");
        r.execute(de, "gemaelde", new String[]{"rückgängig"});
        verify(plugin).handle(de, "gemaelde", "undo");
        Player zh = player(Locale.SIMPLIFIED_CHINESE);
        r.execute(zh, "画廊", new String[]{"撤销"});
        verify(plugin).handle(zh, "画廊", "undo");
        r.execute(zh, "画廊", new String[]{"幻灯片", "关"});
        verify(plugin).handle(zh, "画廊", "slideshow", "off");
    }

    @Test
    void givenConsole_whenReloadOrNoArguments_thenOldLogicElsePlayersOnly() throws Exception {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        when(console.hasPermission(anyString())).thenReturn(true);
        doAnswer(i -> { chat.add(PlainTextComponentSerializer.plainText().serialize((Component) i.getArgument(0))); return null; })
                .when(console).sendMessage(any(Component.class));
        CommandRegistration r = registration(plugin);
        r.execute(console, "vgallery", new String[]{"reload"});
        verify(plugin).handle(console, "vgallery", "reload");
        r.execute(console, "vgallery", new String[0]);
        verify(plugin).handle(console, "vgallery");
        r.execute(console, "vgallery", new String[]{"random"});
        assertEquals(List.of("Only players can do that."), chat);
    }

    @Test
    void givenMissingPermissions_whenRun_thenRefused() throws Exception {
        CommandRegistration r = registration(plugin);
        r.execute(player(Locale.US), "vgallery", new String[]{"reload"});
        Player nobody = mock(Player.class);
        when(nobody.locale()).thenReturn(Locale.US);
        r.execute(nobody, "vgallery", new String[]{"random"});
        verifyNoInteractions(plugin);
    }

    @Test
    void givenHelp_whenGerman_thenLocalizedUsageAndDescriptions() throws Exception {
        registration(null).execute(player(Locale.GERMANY), "gemaelde", new String[]{"hilfe"});
        String all = String.join("\n", chat);
        assertTrue(all.contains("/gemaelde [MOTIV]"), all);
        assertTrue(all.contains("/gemaelde diashow [SEKUNDEN|aus] – Diashow auf dem Gemälde"), all);
        assertFalse(all.contains("neuladen"), all);
    }

    @Test
    void givenTree_whenChecked_thenNoNameConflictsAndRootAliases() throws Exception {
        CommandRegistration r = registration(null);
        assertEquals(List.of(), r.conflicts());
        assertEquals("[gemaelde, gemälde]", "[" + r.lang().raw("de", "commands.vgallery.aliases").replace("\n", ", ") + "]");
        assertEquals("画廊", r.lang().raw("zh_cn", "commands.vgallery.aliases"));
    }

    @Test
    void givenLanguageFiles_whenCompared_thenSameCommandKeysAndADescriptionForEveryNode() throws Exception {
        Set<String> english = null;
        for (String code : LANGUAGES) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(
                    GalleryCommandsTest.class.getResourceAsStream("/lang/" + code + ".yml"), StandardCharsets.UTF_8));
            Set<String> keys = new TreeSet<>();
            for (String key : yaml.getKeys(true)) if (key.startsWith("commands.")) keys.add(key);
            if (english == null) english = keys; else assertEquals(english, keys, code);
            List<CommandSpec> nodes = new ArrayList<>();
            collect(GalleryPlugin.commandSpec(null), nodes);
            for (CommandSpec node : nodes) assertFalse(yaml.getString(node.langPath() + ".description", "").isBlank(), code + " " + node.langPath());
        }
    }

    private static void collect(CommandSpec node, List<CommandSpec> out) {
        out.add(node);
        for (CommandSpec child : node.children().values()) collect(child, out);
    }
}
