# Vystorm Gallery

**Change any painting without breaking it.** Right-click a painting with an empty hand, scroll the mouse wheel to flip through every motif that fits the wall, right-click again to keep it. Sneak + right-click flips one motif at a time.

- Every vanilla motif plus motifs added by datapacks, ordered small to large.
- `mode: fit` lets the painting grow or shrink into any motif that fits the free wall space; `mode: same-size` keeps its size.
- Hotbar stays put while you scroll; scroll mode ends after 20 seconds idle, when you walk away or the painting is gone.
- Protection plugins win: if a region plugin blocks interacting with the painting, nothing changes.
- The action bar shows motif, size and position ("Kebab 1×1 · 3/24"); the click sound is higher for small and deeper for big motifs.
- Hang a new painting and you are already in scroll mode: pick the motif on the spot.
- `/vgallery` opens a menu with every motif as a painting icon; click one to hang it; more than 54 motifs get pages, the current motif glints. `/vgallery random` picks a surprise, `/vgallery <motif>` hangs one by name.
- Painting brush: sneak + right-click a painting with a brush to pick up its motif, right-click other paintings to apply it.
- Favourite motifs: shift-click a motif in the motif menu to mark it; your favourites come first, with a star, in every motif menu.
- Texts in seven languages (`lang/en.yml`, `de`, `fr`, `it`, `pt_br`, `ru` and `zh_cn`) through the Vystorm Core language system; `config.yml` is editable in game with `/vadmin config`.
- Needs [Vystorm Core](https://www.spigotmc.org/resources/139848/) 0.66.0 or newer (free). What the build checks: Java 21 bytecode (`--release 21`), compiled against the Paper API 1.21.11 and, in `compat1206` and `test1206`, against Paper 1.20.6. Use the Java version your server needs. Tested on Paper and Purpur 26.2, Paper 1.20.6 and Spigot 26.2.

## Motif album (1.14.0)

Every motif you hang is collected in your album (`/vgallery album`). Complete a whole painting size for a one-time reward.

## Painting lock

Your painting, your motif. Click the padlock in the motif menu (or `/vgallery lock` while looking at a painting) and only you can change its motif or take it down.

- Other players cannot scroll, brush, pick or randomize a locked painting; they see who locked it.
- Others and their arrows cannot break it (`lock.protect-break`), explosions leave it hanging (`lock.protect-explosions`). A removed wall still drops it.
- Click the padlock again to unlock. Staff with `vystormgallery.bypass` can change, break and unlock any painting.
- Protection plugins still win: you can only lock paintings you are allowed to use. To ask them, Gallery fires a test right-click on the painting (`at.esoren.vystormgallery.ProtectionProbe`, a `PlayerInteractEntityEvent`); after the last `HIGHEST` listener it is cancelled, so loggers and other plugins that skip cancelled events never treat it as a real click. Plugins that react to painting clicks at lower priorities can skip it with `instanceof ProtectionProbe`. The same check runs before starting a slideshow.
- Stored on the painting itself: no data file, survives restarts, gone when the painting is broken.

## Slideshow paintings

Turn any painting into a living picture that shows the next motif every few seconds. Click the clock in the motif menu (left-click: start/stop, right-click: next interval) or use `/vgallery slideshow [seconds|off]`, e.g. `/vgallery slideshow 5m`.

- Same size only by default, so a wall of paintings never shifts (`slideshow.same-size`); optional random order (`slideshow.shuffle`).
- Paintings only change while a player is nearby (`slideshow.player-range`) and only in loaded chunks: no lag from forgotten slideshows.
- Stored on the painting itself: keeps running after restarts and chunk reloads. Excluded motifs stay excluded.
- A locked painting's slideshow can only be started or stopped by its owner.

## Undo a motif change

Look at the painting and run `/vgallery undo` to restore the motif before your last successful manual change (scroll, sneak-click, brush, menu or command). One step is kept per player until logout or plugin restart. A later Gallery change to that painting, including a slideshow step, invalidates the step; undo also refuses if the current motif has changed externally.

Locks, region protection, excluded motifs, size mode and available wall space are checked again. Failed attempts keep the step for retrying. Undo ends your scroll session; it does not stop a slideshow. Uses `vystormgallery.use`, with no new config keys.

## Slideshow playlists

Build a themed slideshow for each painting. Choose a motif with the menu or brush, run `/vgallery playlist add`, and repeat for the other motifs. Start playback with `/vgallery slideshow 30`. `/vgallery playlist` shows the saved order; `remove` removes the current motif and `clear` returns to all allowed motifs. Up to 54 distinct motifs are stored on the painting and survive chunk unloads and restarts.

Playback follows insertion order (or random order with `slideshow.shuffle`). Missing datapack motifs, excluded motifs, incompatible sizes and motifs that do not fit are skipped; if none can be shown, the painting stays unchanged. An empty playlist uses the normal slideshow selection. Editing a playlist does not start or stop playback or change the displayed motif. Stop playback while assembling a list if you want the selected motif to stay put. Locks and protection plugins are checked before accessing the list.

Uses `vystormgallery.use` and `vystormgallery.slideshow` and the existing `slideshow` settings, including `enabled` and `same-size`, and since 1.18.0 the switch `slideshow.playlists.enabled`; no new permissions. German: `/gemaelde motivliste [hinzufügen|entfernen|leeren|anzeigen]`; Chinese: `/画廊 播放列表 [添加|移除|清空|查看]`. English names work in every language.

Use `/vgallery playlist first` or `/vgallery playlist last` to move the currently displayed motif to the beginning or end of its saved playlist. Other motifs keep their order. Missing motifs are not added, the displayed painting stays unchanged, and existing locks and region protection still apply. German: `/gemaelde motivliste anfang` or `ende`; Chinese: `/画廊 播放列表 置顶` or `置底`. Random playback still uses the configured shuffle behavior.

## Commands and permissions

| | |
|---|---|
| `/vgallery [menu]` | motif menu for the painting you look at, with lock and slideshow buttons |
| `/vgallery undo` | undo your last manual motif change on the painting you look at (`vystormgallery.use`) |
| `/vgallery random` | random fitting motif |
| `/vgallery <motif>` | hang a motif by name, e.g. `skull_and_roses` (tab completes) |
| `/vgallery lock` | lock or unlock the painting you look at (`vystormgallery.lock`) |
| `/vgallery slideshow [seconds\|off]` | start, change or stop a slideshow; `30`, `30s`, `5m`, `1h` (`vystormgallery.slideshow`) |
| `/vgallery playlist [add\|remove\|first\|last\|clear\|list]` | manage the looked-at painting's saved slideshow motifs; add/remove use its current motif (`vystormgallery.slideshow`) |
| `/vgallery reload` | reload `config.yml` (`vystormgallery.admin`, op) |
| `vystormgallery.use` | change paintings (default: everyone) |
| `vystormgallery.lock` | lock paintings (default: everyone) |
| `vystormgallery.slideshow` | slideshow paintings and manage their playlists (default: everyone) |
| `vystormgallery.bypass` | change, break and unlock locked paintings (default: op) |

## Switches

Every feature has an on/off switch in `config.yml` (all on by default, listed in Vystorm Core's `/vadmin features`, applied without a restart except `tutorial.enabled`): `gallery.enabled` (the whole plugin), `scroll-mode.enabled`, `sneak-click-flips`, `resize.enabled`, `excluded-motifs.enabled`, `sound`, `action-bar.enabled`, `select-on-place`, `motif-menu.enabled`, `brush`, `favourites`, `lock.enabled`, `lock.protect-break`, `lock.protect-explosions`, `slideshow.enabled`, `slideshow.playlists.enabled`, `undo.enabled`, `album.enabled`, `exhibitions.enabled`, `tours.enabled`, `protection-check.enabled`, `tutorial.enabled`. A switched-off feature answers "This feature is switched off on this server."; locks, slideshows, playlists, albums, favourites and exhibitions stay saved.

## Placeholders

With Vystorm Core 0.65.0 the plugin registers the namespace `gallery` (server thread; empty while the feature is switched off): `album_found`, `album_total`, `album_groups_complete`, `album_groups_rewarded`, `exhibitions_owned`, `exhibition_votes`, `exhibitions_published`, `touring`. Examples: `%vystorm_gallery_album_found%` (motifs the player discovered) and `{gallery:exhibitions_published}`. `/vadmin placeholders gallery` lists them.

## Config

```yaml
lock:
  enabled: true
  protect-break: true        # others and their arrows cannot break locked paintings
  protect-explosions: true   # explosions leave locked paintings hanging
slideshow:
  enabled: true
  default-seconds: 30
  intervals: [10, 30, 60, 300]   # menu right-click steps through these
  min-seconds: 5
  max-seconds: 3600
  same-size: true            # only motifs of the painting's size
  shuffle: false
  player-range: 32           # only change while a player is this close
  sound: false
```

## Building

```bash
./gradlew build
```

The jar is `build/libs/VystormGallery-<version>.jar`.

On 1.20.6, the chest menu uses ordinary painting icons and readable motif names instead of native motif previews/titles. Only motifs available on the server are offered; newer vanilla and datapack painting variants require a server that supports them. Scrolling, brush, favourites, locks, undo and slideshows remain available. Core supplies compatibility helpers and a chat fallback for its config dialogs.

Build with JDK 25 and `-PvystormCoreJar=<Core 0.66.0+ jar>`. All source sets target Java 21. The `modern` source set isolates current Paper APIs and is included in the same jar. `check` includes `compat1206` (main sources compiled against the old API) and `test1206` (a server-free linkage/fallback smoke check against those libraries). Set `-Ppaper1206Libraries=<directory>` to override the default `<home>/source/tools/smoke-1206/libraries` location. The offline build needs the normal Gradle dependencies cached as well.

## Painting exhibitions

Use `/vgallery exhibitions` or the Core menu to create named exhibitions, add paintings you are looking at, edit the title and description, and explicitly publish a private draft. Existing painting locks and region protection checks still apply when adding a painting. This feature catalogs paintings and coordinates; it does not move paintings or grant access to protected areas.

Visitors can browse published exhibitions and toggle their single vote. Owners cannot vote for their own exhibition. Changing the paintings resets its votes; removing its last painting makes it private. Hiding an exhibition immediately blocks votes from already-open menus. Unloaded or removed paintings show their saved information without loading chunks.

Metadata and votes persist atomically in `exhibitions.yml`. Limits: 10 exhibitions per player, 60 paintings each, 64 characters for titles and 240 for descriptions. Invalid files are retained with editing disabled. All new messages are available in all seven bundled languages.

## License

Copyright (c) 2026 Esoren / Vystorm

Versions after 1.16.1 are licensed under the GNU General Public License v3.0 (GPL-3.0), see [LICENSE](LICENSE).
Version 1.16.1 and earlier were released under the MIT License ([LICENSE-MIT-until-1.16.1.txt](LICENSE-MIT-until-1.16.1.txt)) and stay available under those terms.
