## 1.18.0 — 2026-11-18

- Requires Vystorm Core 0.65.0 or newer. Update Core before you update this plugin.
- Every feature can now be switched on and off in `config.yml`. All switches are on by default, so a server that updates keeps working as before; the new keys are added to your existing `config.yml` with their comments. Each switch is listed in Core's `/vadmin features` with a one-line description in seven languages and applies at once, without a restart (only `tutorial.enabled` needs one).
  - The whole plugin: `gallery.enabled`. Off: paintings behave as in vanilla (no scroll mode, brush, lock protection, slideshows or tours) and every `/vgallery` command except `reload` is switched off.
  - Changing motifs: `scroll-mode.enabled`, `resize.enabled` (off: only motifs with the painting's size), `excluded-motifs.enabled` (off: the excluded list is ignored), `action-bar.enabled`, `motif-menu.enabled` (the menu, `/vgallery random` and `/vgallery MOTIF`), `undo.enabled`.
  - Slideshows: `slideshow.playlists.enabled` (off: slideshows use the normal order; playlists stay on the paintings).
  - Exhibitions: `exhibitions.enabled` (the command, the menus and the Core main menu entry; tours need it).
  - Protection: `protection-check.enabled`. Off: region and protection plugins are not asked before a motif changes; turn it off only on servers without such plugins.
  - Tutorial: `tutorial.enabled` (needs a restart).
  - Keys that already existed keep their names and are now listed as switches too: `sneak-click-flips`, `sound`, `select-on-place`, `brush`, `favourites`, `lock.protect-break`, `lock.protect-explosions`.
- A switched-off feature answers one shared message, "This feature is switched off on this server.", its subcommands are hidden from help and tab completion, and its buttons leave the menus. Locks, slideshows, playlists, albums, favourites and exhibitions stay saved and work again when the switch is back on.
- Tab completion and help follow a switch at once, without registering the command again.
- Placeholders: the plugin registers its values with Vystorm Core under the namespace `gallery`: motifs found and all motifs, completed and rewarded size groups, the player's exhibitions and their votes, published exhibitions, and whether the player is on a tour. Examples: `%vystorm_gallery_album_found%` (motifs the player discovered) and `{gallery:exhibitions_published}`. `/vadmin placeholders gallery` lists them. A value of a switched-off feature is empty.
- The off messages of the album and of tours are now the shared message.

## 1.17.0 — 2026-11-17

- Requires Vystorm Core 0.64.0 or newer. Update Core before you update this plugin.
- The order in which Vystorm plugins react to the same event is now set in one place, Vystorm Core (`event-order` in Core's `config.yml`). The painting lock (breaking and explosions) now denies before other plugins act.
- A damaged `tours.yml` is now set aside instead of being overwritten by the next save.
- License: GPL-3.0 from this version. Earlier versions stay under the MIT License.

## 1.16.1 — 2026-11-16

- Tab completion for `/vgallery exhibitions`: the first word now suggests the available actions (create, open, add, title, description, publish, hide, delete, tour, caption, stop-tour).

## 1.16.0 — 2026-11-15

- Rebuild the motif picker, exhibition browser and exhibition owner tools as Core Panels with selection, paging, tabs and localized controls.
- Keep all three chest views as fallbacks for unavailable Panels or older Core. No item-moving menus were found; existing album and playlist dialogs remain unchanged.

## 1.15.0 — 2026-11-12

- Guided tours: a published exhibition with two or more paintings gets a **Tour** button. Visitors walk to each painting in order (the action bar shows the way) and read the caption of each stop; owners set captions by looking at a painting and using `/vgallery exhibitions caption NAME TEXT`. Finishing a tour gives a reward once per exhibition (owners get none for their own). `/vgallery exhibitions stop-tour` ends a tour. Settings under `tours`, also in the first-run setup.

## 1.14.0 — 2026-11-11

- Motif album: every motif you hang is collected. `/vgallery album` shows your progress per painting size (1×1, 2×1, 4×4, …); completing a whole size earns a one-time reward (50 XP by default, optional console commands under `album.reward-commands`).

## 1.13.0 — 2026-11-11

- Short tutorial in the main menu (/vmenu → Tutorials) in all seven languages.
- /gallery works as a short name for /vgallery.

## 1.12.0 — 2026-11-10

- First-run setup: when the plugin is installed for the first time, admins answer a few questions or take the recommended settings, with a preview before anything is saved (needs Vystorm Core 0.54.0 or newer).
- All settings, including extra configuration files, appear in Core's new `/vadmin config` menu.

## 1.11.1 (2026-11-08)

- Every motif change, scroll session, brush copy and unlock rechecks permission, game mode, lock and region protection at the moment of the change; protection probes are never fired for ineligible players.
- Scroll sessions end when the player walks away, times out or loses the permission instead of changing the painting.
- Menu clicks act one tick after the click and only while the menu is still open (no inventory changes inside the click event).
- Damaged exhibition files (wrong text types, fractional or out-of-range coordinates, invalid motifs) are kept unchanged instead of being coerced and overwritten; re-adding a painting keeps its place and the votes.
- Non-finite distances fall back to the defaults, oversized durations are rejected, and a lower max-seconds applies to running slideshows after a reload.

## 1.11.0 - Painting exhibitions

Use `/vgallery exhibitions` or the Core menu to create named exhibitions, add paintings you are looking at, edit the title and description, and explicitly publish a private draft. Existing painting locks and region protection checks still apply when adding a painting. This feature catalogs paintings and coordinates; it does not move paintings or grant access to protected areas.

Visitors can browse published exhibitions and toggle their single vote. Owners cannot vote for their own exhibition. Changing the paintings resets its votes; removing its last painting makes it private. Hiding an exhibition immediately blocks votes from already-open menus. Unloaded or removed paintings show their saved information without loading chunks.

Metadata and votes persist atomically in `exhibitions.yml`. Limits: 10 exhibitions per player, 60 paintings each, 64 characters for titles and 240 for descriptions. Invalid files are retained with editing disabled. All new messages are available in all seven bundled languages.

## 1.10.0 - Spigot and CraftBukkit compatibility

- Require Vystorm Core 0.51.0 or newer.
- Use shared Bukkit registry keys and Core messaging/entity lookups; keep the optional modern Paper painting adapter isolated.
- Validation: 65 automated checks (0 skipped), plus combined startup, listener registration and Core diagnostics/help on official Spigot26.3 and CraftBukkit26.3.
- Platform detail: On Spigot, painting menu icons use ordinary painting items; native modern painting-item previews remain a guarded Paper capability.

## 1.9.0 (2026-11-06)

- Add complete French (`fr`), Brazilian Portuguese (`pt_br`), Italian (`it`) and Russian (`ru`) language bundles. Minecraft client locales and the Core language selector choose them automatically.
- Validate packaged language keys, placeholders, formatting and tutorial coverage in server-loader tests.

## 1.8.0 (2026-11-05)

- Move the current motif to either end of a slideshow playlist with `/vgallery playlist first` and `last`, without rebuilding the list or changing the painting. Existing permissions, locks and region checks apply. Includes English, German and Simplified Chinese commands and feedback.

# Changelog

## 1.7.0 (2026-11-03)

- Runs on Paper 1.20.6 – 26.x. Java 21 bytecode and minimum API 1.20.6; requires Vystorm Core 0.48.0+ for feature detection and inventory compatibility.
- Isolated modern painting registry, titles and item previews in a bundled adapter, selected by available API features. On 1.20.6 menus use ordinary painting icons and readable motif names; only server-supported motifs are available. No Gallery editing or slideshow feature is disabled.
- Added offline `compat1206` compilation and a real 1.20.6 API linkage/fallback smoke check to `check`, plus adapter selection tests.

## 1.6.0 (2026-11-03)

- **Slideshow playlists**: `/vgallery playlist [add|remove|clear|list]` manages up to 54 distinct motifs per painting. Add or remove the currently displayed motif; playback follows the saved insertion order or the existing shuffle setting. Lists persist on the painting across restarts and chunk reloads. Clearing the list restores the normal slideshow selection.
- Missing, excluded, wrong-sized or non-fitting motifs are skipped without substituting unrelated motifs. Playlist access respects painting locks, region protection, spectator mode and the existing use/slideshow permissions. Uses existing slideshow settings; no new config keys.
- Localized commands, help and messages in English, German and Simplified Chinese, with tests for persistence, limits, access checks, candidate selection and command routing.

## 1.5.0 (2026-11-02)

- **Undo motif changes**: `/vgallery undo` restores the motif before your last successful manual change on the painting you look at. One step per online player, cleared by later Gallery changes to that painting (including slideshow steps). Locks, protection plugins, configured motifs and wall space are checked again. Uses `vystormgallery.use`; no new config keys.
- Localized command, help and messages in English, German (`rückgängig`) and Simplified Chinese (`撤销`).

## 1.4.0 (unreleased)

- **Favourite motifs**: shift-click a motif in the motif menu to mark it as a favourite. Favourites are shown first, with a star, in every motif menu you open, so the motifs you use for your builds are always on the first page; shift-click again removes the mark. Stored on the player (up to 54). Config `favourites` (default `true`).

## 1.3.0 (unreleased)

Needs Vystorm Core 0.45.0.

- Simplified Chinese (zh_cn) translation.
- `/vgallery` now runs through Vystorm Core's command system: subcommands, help, tab completion and error messages in the player's language (`/vgallery zufall`, `/vgallery 随机`, `/vgallery diashow aus` work next to the English names for everyone), a clickable `/vgallery help` (`hilfe`, `帮助`), a "did you mean" for typos and an entry in `/vhelp`. A word that is no subcommand still hangs the motif of that name. New localized root commands `/gemaelde`, `/gemälde` (German) and `/画廊` (Chinese) from the language files (`commands.vgallery.*`); which languages are active is set server-wide in Core (`commands.localized-aliases`). `/vgallery` and every English subcommand stay. The command is no longer declared in plugin.yml.

## 1.2.0 (2026-10-30)

- **Painting lock**: the padlock in the motif menu or `/vgallery lock` locks a painting to its owner. Others cannot change its motif (scrolling, sneak-click, brush, menu, `/vgallery random`, `/vgallery <motif>`) and see who locked it; others and their projectiles cannot break it (`lock.protect-break`) and explosions leave it hanging (`lock.protect-explosions`). `vystormgallery.bypass` (op) can change, break and unlock any painting. Locking asks the protection plugins first. Stored on the painting itself, no data file.
- **Slideshow paintings**: the clock in the motif menu (left-click start/stop, right-click next interval) or `/vgallery slideshow [seconds|off]` makes a painting show the next motif every few seconds (`30`, `30s`, `5m`, `1h`). Same-size motifs by default so walls never shift, optional random order, only while a player is within `slideshow.player-range` and only in loaded chunks. Keeps running after restarts (stored on the painting).
- Motif menu: lock and slideshow buttons at the end of the last row (between the arrows when the menu has pages). With the vanilla motifs everything still fits one page.
- New permissions `vystormgallery.lock` and `vystormgallery.slideshow` (default: everyone), `vystormgallery.bypass` (op). New config sections `lock` and `slideshow` (both on by default); existing configs get the defaults automatically.
- New texts in `lang/en.yml` and `lang/de.yml`; `usage` mentions `lock` and `slideshow`.

Fixed (before release):

- The test right-click Gallery fires to ask protection plugins before locking a painting or starting a slideshow was a plain click event, so other plugins (loggers such as CoreProtect, click rewards, other painting plugins) could treat it as a real click. It is now its own event type (`ProtectionProbe`, documented in the README) that is cancelled right after the protection plugins answered (end of `HIGHEST`), so `MONITOR` listeners and listeners that skip cancelled events never see it; a denial from a later listener still counts.

## 1.1.0 (2026-10-29)

- `/vgallery <motif>` hangs a motif by name on the painting you look at (e.g. `/vgallery skull_and_roses`, namespace optional for datapack motifs). Tab completion lists every motif that is not excluded; mode and `excluded` are respected.
- The motif menu gets pages when a server has more than 54 motifs (datapacks): 45 motifs per page with arrows in the bottom row. Before, motifs past the 54th were missing. The menu opens on the page with the painting's current motif, which now glints and is marked "Current motif".
- New texts in `lang/en.yml` and `lang/de.yml`: `menu-title-page`, `menu-current`, `menu-previous`, `menu-next`, `unknown-motif`; `usage` mentions the motif name.

## 1.0.1 (2026-10-28)

- Registers itself for the Vystorm Core update check (Core 0.40.2+); older Cores are unaffected.

## 1.0.0 (2026-10-28)

- First release: right-click + scroll to flip painting motifs, sneak + right-click flips one, fit/same-size mode, excluded motifs, datapack motifs, English and German messages, `/vgallery reload`.
- Built on Vystorm Core: Core language system (`lang/en.yml`, `lang/de.yml`), in-game config editor (`/vadmin config`), Core task groups and atomic file writes.
- Motif menu (`/vgallery`), `/vgallery random`, painting brush, motif choice right after hanging, size and position in the action bar, pitch by size.
