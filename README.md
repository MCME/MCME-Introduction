# MCME-Introduction

The new-player introduction of the MCME (Minecraft Middle-earth) network. A player who joins the hub
for the first time passes through two short "rooms" before reaching the hub itself:

1. **The welcome room** shows a welcome screen, a link to the rules and a welcome text, with
   "Press "t" for help." on the action bar.
2. **The compatibility room** is only for players whose client needs attention: a Minecraft version
   MCME doesn't support, Forge or OptiFine, or Fabric without MCME's own installer. It shows a
   warning screen and explains what to do. Everyone else skips it.

After the rooms the player gets a welcome title and a series of tips as advancement toasts. While a
player is in a room, chat from the rest of the network is hidden, so the screens can be read.

One jar serves both sides of the network and is installed unchanged on each:

- **Paper backend plugin** (`com.mcmiddleearth.introduction.paper.IntroductionPlugin`): the rooms,
  the compatibility checks and reminders, the tips, the glowing items, and the `/intro` and
  `/confirm` commands.
- **Velocity proxy plugin** (`com.mcmiddleearth.introduction.velocity.IntroductionPlugin`, id
  `mcme-introduction`): hides proxy chat from players a backend reports as in a room.

## How the rooms work

- **Entering a room.** A room is a box in the world. A player who moves or is teleported into it is
  placed at the room's `playerLocation`, switched to spectator mode, and shown the room's camera
  overlay (the full-screen picture) through an item on their head. The previous head item and game
  mode come back when they leave; a player who came in spectator mode gets the server's default
  game mode instead. Spectator mode's own ways out, the "teleport to player" menu and spectating an
  entity, don't work in a room.
- **Leaving a room.** The player steps to the **left** (the strafe-left key, A by default). The screen
  fades to black and the player moves on: from the welcome room to the compatibility room or past it,
  from the compatibility room to the hub.
- **Who skips the compatibility room:** a client whose version is in `supportedVersions` and that
  is vanilla, or Fabric with MCME's marker mod (detected by MCME-Architect). Everyone else gets it,
  and can leave it in two ways:
  - **confirm**, which ends the room and its reminders for good: `/confirm optifine` for a
    Forge-family client (Forge, NeoForge, LiteLoader, OptiFine) on a supported version,
    `/confirm ignore` for every other client;
  - **step left**, which means "remind me later".

  A confirmation is remembered, so the room is skipped next time; `/confirm reset` undoes it.
- **Reminders.** A player whose client gets the compatibility room and who hasn't confirmed sees its
  warning screen again for a few seconds, with its text in chat, every `reminderPeriod`: after
  stepping out of the room, and on joining any server with the plugin outside the rooms.
- **Tips.** Leaving the last room starts the tips: a title, a start message, a broadcast, then one
  advancement toast per tip. The broadcast goes to the whole network (through MCME-Connect) only the
  first time a player finishes; after that, only the player sees it. Entering a room stops the tips;
  leaving the rooms again starts them anew.
- **Joining.** A player who joins inside a room continues there. A player who joins outside the rooms
  gets the reminders if they apply, and the rest of their tips on the server where the tips ran.

## The resource pack

The room screens are textures from MCME's resource pack, which MCME-Architect sends. A room waits for
the pack before it shows its screen and its chat text, so the two arrive together:

- If the pack fails (declined, download failed, …), the player gets the matching `rpMessages` text.
- If no pack is offered at all, for example to a client version without a pack, the room sends its
  chat text anyway after 10 seconds. There is no screen without the pack.
- If the pack hasn't loaded after about 250 seconds, the player gets `messageRpTimeout`.

## Hidden chat

While a player is in a room, **system chat** is hidden from them. System chat is what chat plugins
like VentureChat normally send, plus join, leave and broadcast messages.
- **On the backend**, ProtocolLib drops it. The plugin's own messages still get through, because the
  message as a whole (its root component) carries the insertion `mcme:intro`.
- **On the proxy**, VPacketEvents drops the system chat the proxy handles for that player, such as the
  network's join and leave messages. The backend tells the proxy when a player enters and leaves the
  rooms. The proxy also ends the silence when the player leaves the network or arrives on a server.

**Player chat** (`/say`, `/me`, signed chat) is not hidden. Since Minecraft 1.21.5 the client numbers
player chat messages and disconnects when one is missing, so dropping them is not safe.

Chat comes back `unsilenceDelay` ticks after the player leaves the last room.

## Requirements

| Component | Version |
|-----------|---------|
| Paper | 26.2 (`api-version: '26.2'`, compiled against `paper-api 26.2.build.126-stable`) |
| Velocity | 4.2.1 (compiled against `velocity-api 4.2.1-SNAPSHOT`) |
| Java at runtime | 25 |
| Backend, required | MCME-Architect 3.1.0 (sends the resource pack, detects the marker mod), ViaVersion 5.x (client versions), ProtocolLib 5.5.0 or newer (a dev build is needed on 26.x) |
| Backend, required in practice | MCME-Connect 3.0.3 (the rooms listen to its join event; `plugin.yml` doesn't declare it) |
| Proxy, required | VPacketEvents 1.1.0 |
| Optional | a MySQL/MariaDB database for players' `/confirm` choices |

The backend declares `depend: [ MCME-Architect, ViaVersion, ProtocolLib ]`: **Paper won't load the
plugin on a server that lacks one of them.** Before 1.3.0 a typo (`depends:`) made them optional in
practice.

## Installation

1. Put the same jar in the proxy's `plugins/` folder and in the `plugins/` folder of each backend that
   should run it. Only a server with a `locations.yml` has rooms; the other backends show the
   reminders.
2. Start once to get `plugins/MCME-Introduction/config.yml`, then edit it. **The plugin never
   overwrites an existing `config.yml`.** A setting a new version adds is missing from an older file:
   it then takes the value from the jar's own `config.yml` or a built-in one. To see or change it,
   add it by hand.
3. On the server with the rooms, create `plugins/MCME-Introduction/locations.yml` with the rooms'
   positions. The plugin never creates this file.
4. Restart the server, or run `/intro reload`.

[docs/configuration.md](docs/configuration.md) describes every setting of `config.yml` and
`locations.yml`.

## Commands and permissions

| Command | Who | What it does |
|---------|-----|--------------|
| `/intro reload` | `introduction.reload` (op by default) | Reloads `config.yml` and `locations.yml` and rebuilds the rooms, a second after the command. Tells the sender when it starts and when it is done, or that it failed (the error is in the console); a second reload in that second is ignored. Players in a room leave it (game mode, head item and chat restored) and enter it again at their next move; a reminder's warning screen comes off, and the tips carry on. Changed advancement texts (the room and tip toasts) take effect at the next clean restart. |
| `/intro glowitem <player> on\|off` | the console, or another sender that isn't a player | Turns the glowing items on or off for a player (see `itemGlow` in the configuration). |
| `/confirm optifine` | players with a Forge-family client on a supported version | Leaves the compatibility room and stops its reminders for good. |
| `/confirm ignore` | every player except those `/confirm optifine` is for | Leaves the compatibility room and stops its reminders for good. |
| `/confirm reset` | every player (not the console) | Forgets the player's confirmations, so the compatibility room and its reminders come back. |

## Data the plugin keeps

In `plugins/MCME-Introduction/`:

- `finishedPlayerList.uid`: the players who finished the introduction once, one UUID per line. They
  get no network-wide broadcast when they finish again. Each new finisher is added as a line at the
  end; blank lines are skipped, and a line that is no UUID is skipped with a warning in the log.
- `EnabledGlowItems.dat`: the players with glowing items on, one UUID per line.
- `flow/YYYY-MM-DD.jsonl`: the flow log, a JSON line for each step a player takes through the
  rooms (entering, the resource pack's result, leaving and how, finishing, reminders, `/confirm`),
  one file per UTC day, kept 90 days. It is meant for tools such as hub-flow;
  [docs/flow-log.md](docs/flow-log.md) describes it.

With `database.enabled: true`, players' `/confirm` choices are stored in the table `confirm_data`
(`player_uuid`, `optifine`, `ignore_confirmed`), which the plugin creates; a player's choices are
loaded when they log in, before they join, on every server that uses the database, so a
`/confirm reset` on one server counts on the others at the next login. **Without the database, each server
keeps the choices in memory until it restarts**, so confirming on the hub doesn't stop the reminders
on another server. The plugin uses the MySQL driver that comes with Paper.

The room and tip advancements are registered when the plugin starts. Paper saves them as files in the
server's built-in `bukkit` datapack; the plugin deletes those files when it shuts down cleanly. After
a crash it reuses the files left behind, with their texts, until the next clean restart.

## Building

The build needs **JDK 25** and Maven.

```bash
mvn package
```

This runs the tests and produces `target/MCME-Introduction-<version>-<type>-<commit>.jar`, for
example `MCME-Introduction-1.3.0-RELEASE-01b2efd.jar`. The commit id tells builds of one version
apart. `<type>` defaults to `RELEASE`; pass `-Dbuild.type=DEV` for a test build.

- **The Velocity side uses internals of the Velocity proxy**, which no Maven repository publishes.
  Put a Velocity 4.2.1 proxy jar (from [PaperMC's downloads](https://papermc.io/downloads/velocity))
  at `libs/velocity-proxy.jar`, or pass `-Dvelocity.proxy.path=/path/to/velocity.jar`. `*.jar` is
  ignored by git.
- **Dependencies** resolve from the MCME Maven repository, `https://repo.mcmiddleearth.com`:
  MCME-Architect and MCME-Connect from `releases`, the rest through its `mirror` or the other
  repositories the pom lists. Nothing needs a local `mvn install`.
- **The Velocity main class is generated.** `src/main/resources/templates/IntroductionPlugin.java` is
  filtered with the pom's `plugin.*` properties at build time. The file of the same name under
  `src/main/java/.../velocity/` is an empty placeholder: edit the template.
- **In a linked git worktree** the commit id in the jar name comes from the main checkout's HEAD (a
  bug in git-commit-id-maven-plugin). Pass `-Dgit.commit.id.abbrev=$(git rev-parse --short=7 HEAD)`,
  or build releases from a plain clone.

## Tests

`mvn test` runs the MockBukkit 26.2 test suite for the Paper side. `PluginTestBase` starts a mock
server with the plugin loaded. It fills in ProtocolLib's protocol manager and keeps advancements the
way Paper keeps them; MockBukkit ignores `depend:`, so the other plugins are simply absent.

Tests that let the rooms check the resource pack replace `Room.rpPlayerData`: MCME-Architect's
`RpManager` can't load without a running Architect. Tests that put players in the rooms extend
`RoomTestBase`, which gives every player a 26.2 client without MCME's marker mod, by replacing
`SecondRoom.clientVersion` (ViaVersion) and `SecondRoom.mcmeMarker` (MCME-Architect); its `addRooms`
builds two rooms in a world. Flow log tests read the log back with `FlowLogFiles` after
`getFlowLog().close()`, which writes the events still waiting.

Not covered by tests:
- the Velocity side;
- the ProtocolLib call that reads a packet's message, because ProtocolLib's packet classes need a
  real server.

MockBukkit marks a test that reaches an unimplemented method as **skipped**, not failed: check the
skipped count.

## Branches and releasing

`master` holds releases and `development` collects the work. Both are protected: changes go through
pull requests into `development`.

To release:
1. Set the version in `pom.xml`. The Velocity descriptor and main class take it from there.
2. Add the version's section to `CHANGELOG.md`.
3. Open a pull request from `development` into `master`.
4. After the merge, tag the merge commit `vX.Y.Z`.
5. Build the jar from a plain clone of the tag, and attach it with its SHA-256 to a GitHub release.

## License

GPL-3.0, see [LICENSE](LICENSE).
