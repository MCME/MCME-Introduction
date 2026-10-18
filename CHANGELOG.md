# Changelog

## [1.4.0] - unreleased

### Added
- The flow log: a JSON line for each step a player takes through the rooms, in
  `plugins/MCME-Introduction/flow/YYYY-MM-DD.jsonl`. It records entering a room (with the client's
  version, brand, marker mod, and the warning shown), the resource pack's result and how long it
  took, leaving a room (how, and after how long), being passed over a room, finishing, reminders and
  `/confirm`. It is written for tools such as hub-flow; `docs/flow-log.md` describes it. On by
  default: `flowLog.enabled` and `flowLog.keepDays` (90) in `config.yml`, where a file without them
  gets these values.

### Fixed
- A player who joined standing in the compatibility room, with a client that no longer needs it,
  was passed on twice: the black screen and the welcome title came twice and the tips started over.
- That player's system chat also stayed hidden afterwards, on the server and on the proxy.
- A player who left the server within the join delay was still put in the room. Other delayed steps
  also carried on with a player who had left: a quit during the black screen still started the tips
  (and the "just joined" broadcast), and reminders still ran for them.
- A pack that loaded at a room's last check also sent the time-out message.

## [1.3.0] - 2026-10-12

The 26.2 release. Tested on MCME's dev network in three rounds.

### Added
- `supportedVersions` in `config.yml`: the client versions that skip the compatibility room. A listed
  version covers every release that shares its protocol (`26.1.2` also covers 26.1 and 26.1.1). A
  `config.yml` without the setting gets the shipped list, 26.2 and 26.3. An empty list supports only
  the server's own version, which was the only rule before.
- `<supported_versions>` in the compatibility room's messages shows that list. The shipped messages
  use it instead of a hand-written "1.21.4, 1.21.1", and instead of the text
  `<supported game versions>`, which players saw as written.
- When no resource pack is offered at all, a room sends its chat text after 10 seconds. Before, the
  player saw nothing until a time-out about 250 seconds later.
- A MockBukkit test suite for the Paper side (33 tests).
- This README, a configuration reference (`docs/configuration.md`) and this changelog.

### Changed
- Built against Paper 26.2 (`api-version: '26.2'`), MCME-Architect 3.1.0, MCME-Connect 3.0.3,
  ProtocolLib 5.x and ViaVersion 5.12.1.
- **The backend now requires MCME-Architect, ViaVersion and ProtocolLib.** 1.2.0's `plugin.yml` had
  `depends:` instead of `depend:`, so Paper loaded it without them.
- **Only system chat is hidden in the rooms.** Player chat (`/say`, `/me`) shows. Since Minecraft
  1.21.5 the client disconnects when a numbered player chat message is missing.
- `/intro reload` no longer reloads the whole server's data (`/minecraft:reload`). A changed
  advancement text takes effect at the next restart.
- Entering a room stops a player's running tips; leaving the rooms starts them anew.
- The installer links in the shipped messages open the newest installer release.
- The room positions, which the plugin only reads from `locations.yml`, are no longer listed in the
  shipped `config.yml`; it shows a commented `locations.yml` example instead.

### Fixed
- The room listener was registered twice, so every room event was handled twice.
- After `/intro reload`, players' saved `/confirm` choices were no longer loaded at login.
- Chat came back while a player was still in the compatibility room.
- A start after a crash failed with `Advancement mcme:introchain0 already exists`.
- A shutdown with a player in a room broke off the plugin's clean-up.
- A leftover overlay item could be handed back as a player's helmet.
- The welcome text display was saved with the world, so every crash left another copy.
- `roomEnterMoveDelay` at the top level of `config.yml` was ignored.
- The chat listener crashed on chat packets without a readable message.

### Removed
- Code that nothing called: a TypewriterMC experiment (`KotlinBridge`), an unregistered command
  handler (`IgnoreCommandHandler`), `SecondRoom.clearChat` and an unused record.
- The MySQL driver dependency: the plugin uses the driver that comes with Paper.

## [1.2.1]

Never released. Its changes (`depend:` instead of `depends:`, dependencies resolved from
repo.mcmiddleearth.com) are part of 1.3.0.

## [1.2.0] - 2026-09-21

### Fixed
- The Velocity side loads on Velocity 4: it uses Guice's own `@Inject`, because Velocity 4's Guice 7
  no longer recognises `javax.inject`.

## [1.1] - 2026-02-07

The rewrite of the introduction (September 2025 to February 2026): the welcome and compatibility
rooms with camera overlays, compatibility checks by client version and brand, `/confirm` with
optional database storage, reminders, the tips after the rooms, glowing items, chat hidden in the
rooms on the backend and the proxy, and resource-pack failure messages.
