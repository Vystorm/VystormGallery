package at.esoren.vystormgallery;

import Vystorm.vystorm_core.lang.Lang;
import Vystorm.vystorm_core.panel.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

final class PanelTestSupport {
    static final List<String> LANGUAGES = List.of("en", "de", "fr", "it", "pt_br", "ru", "zh_cn");
    static PanelContext context(Player player, String code) throws Exception {
        Map<String, String> files = new HashMap<>();
        for (String language : LANGUAGES) try (var in = PanelTestSupport.class.getResourceAsStream("/lang/" + language + ".yml")) {
            files.put(language, new String(Objects.requireNonNull(in).readAllBytes(), StandardCharsets.UTF_8));
        }
        Lang lang = Lang.create("vystormgallery", files);
        return new PanelContext() {
            public Player player() { return player; }
            public String arg() { return ""; }
            public PanelSession session() { return null; }
            public String text(String key, Object... args) { return lang.plain(code, key, args); }
            public Component component(String key, Object... args) { return lang.component(code, key, args); }
        };
    }
}
