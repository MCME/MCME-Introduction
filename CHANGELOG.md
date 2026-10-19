# Changelog

## [1.4.1] - unreleased

Fixes from the audit of 2026-10-19.

### Fixed
- A player whose resource pack never loaded and who swapped helmets during a reminder lost the new
  helmet and got the old one back as a copy when they quit. Reminders now end when the pack fails or
  never comes, and a saved head item only goes back over the plugin's own overlay item.
- A server stop, crash or `/intro reload` during a reminder lost the player's helmet, and could
  leave the warning screen on their head for good. The warning screen's item now carries the helmet
  it replaced in its own data (`mcme:intro_head`), so the helmet comes back even from an item a
  crash left on the head, when the player joins outside the rooms or enters one. A warning item the
  player took off turns back into the helmet, and a player who dies during a reminder drops the
  helmet instead of the warning item. After a reload the old reminders no longer came twice.
- A player who rejoined in a room after a crash, or was teleported by staff from one room into the
  other, ended the introduction stuck in spectator mode. A room now keeps the player's own game
  mode in the player's data (`mcme:intro_gamemode`) while they are in it, so it comes back even
  after a crash; staff who enter a room in spectator mode leave it in spectator mode.
- The spectator menu's "teleport to player", and spectating an entity, took a newcomer out of a room
  past the compatibility warning, the tips and the reminders.
- `/intro reload` said nothing, so it was easy to run twice, which loaded everything twice (tips
  doubled, listeners twice). It now reports when it starts and ends or fails, and ignores a second
  reload meanwhile. A reload that failed left the plugin without listeners, silently; it is now
  logged, and a later reload works again.
- A YAML error in `locations.yml` turned every room off without a word; it is now logged as an
  error.
- During a reload a player in the black screen was carried on by the old rooms, a delayed "chat
  back" from before the reload could show chat to a player in a room, and the tips could end, or
  show one tip again, for players whose turn came during the reload. A player who joined just
  before a reload was put in the old room. Now the replaced rooms take nobody in, a join waits for
  the new rooms, the reminders that were running carry on in the new room, and chat that was about
  to come back comes back at once, on the proxy too.
- One blank or cut-off line in `finishedPlayerList.uid` stopped the introduction from starting.
  Bad lines are now skipped with a warning, and new finishers are added as a line at the end instead
  of the whole file being rewritten.
- Newcomers in a room could now and then see a chat message that should be hidden: the lists of
  silenced players were not thread-safe, on the server and on the proxy.
- A player who quit in a room kept their proxy chat hidden (join and leave lines, `/server`
  feedback) until the proxy restarted. The proxy now ends the silence when the player leaves or
  arrives on a server, listens to a backend only about its own player, and ignores a malformed
  message with a warning. "Chat blocked!" is logged at debug level instead of for every line.
- A double click on `/confirm ignore` showed the welcome title twice and started the tips twice.
- A `tpTarget` in a world that isn't loaded, or a typo in `tpTransitionTimes`, froze every newcomer
  who stepped left in the room until a restart.
- A broken entry in `introductionChain.advancementDisplays` made the tips fail every period and the
  shutdown leave players in spectator mode. It is now left out, with a warning naming its key.
- `/confirm reset` from the console threw an error; it is now for players only.
- Chat in which someone typed `mcme:intro` got past the chat filter of the rooms. Only a message
  whose root carries the insertion `mcme:intro` gets through now; one that isn't valid JSON gets the
  old check.
- With the database, a player's choices could arrive after they joined, so a confirmed player got
  the compatibility room again, and a `/confirm reset` on another server was only seen after a
  restart. The choices are now read before the player joins and the row sets both of them. A
  confirmation writes only its own choice, so it can't undo one made on another server. The
  connection times out after 5 seconds, and after a failed read logins skip the database for 60
  seconds instead of each waiting for it. The start-up log no longer says the database was set up
  when it wasn't.

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
