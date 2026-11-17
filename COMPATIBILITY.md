## Spigot/CraftBukkit release 1.10.0

Requires Core0.64.0+. Official Spigot26.3 and CraftBukkit26.3 combined startup and registration tests passed. Existing automated tests and configured older-Paper compilation checks passed. This is not a claim that every gameplay scenario was exercised on each platform.

On Spigot, painting menu icons use ordinary painting items; native modern painting-item previews remain a guarded Paper capability.

## 1.11.0 feature validation

Exact release JAR SHA-256: `f1cb1a359ec52719b69942ad80aa1aee9786dfd1b42eb990d8b23dccc4dc2321`. Automated checks: test: 72 tests, 0 failures, 0 skipped. Older Paper API compilation/compatibility tasks also passed.

Connected-player acceptance on official Spigot 26.3 and CraftBukkit 26.3, including a complete server restart: Actual spawned painting targeted by native raytrace and added through command; draft hidden, publish, inventory vote, hide and stale vote denial; painting references/votes/visibility persist after restart.

All seven new language namespaces passed key and placeholder checks. Independent review found no unresolved concrete defects. Combined acceptance harness and evidence are secured in `vystorm-chat/integration/free-features-2026-11`. These are targeted tests of the new feature, not exhaustive replay of every existing plugin feature or third-party integration.
