# Configuration

MCME-Introduction reads two files in `plugins/MCME-Introduction/` on each backend:

- **`config.yml`**: the rooms' behaviour and texts, the tips, the supported versions, the database.
  The plugin writes the default file at the first start and **never overwrites it**. A setting a new
  version adds is missing from an older file and takes the value described under "When a key is
  missing"; to see or change it, add it by hand.
- **`locations.yml`**: where the rooms are, the welcome text display, and the glowing-item area. The
  plugin never creates it; a server without it has no rooms.

`/intro reload` reads both files again and rebuilds the rooms, and tells the sender whether it
worked; a `locations.yml` that can't be read is an error in the log. Two things need a restart
instead:
- the database settings;
- changed advancement texts (`advancementDisplay`, `advancementDisplays`). They take effect after a
  clean restart; a crash keeps the previous ones until the next clean restart.

Durations are in **ticks** (20 ticks = 1 second), except the title times of the tips, which are in
seconds.

## When a key is missing

The **If missing** columns below say what the plugin uses when your `config.yml` lacks a key:

- ***shipped***: the value from the `config.yml` inside the plugin's jar, which is what a fresh
  install gets. Bukkit uses that file as defaults for these keys. They are listed in the tables with
  the shipped value.
- **any other value**: the plugin's built-in value. For these keys the jar's file isn't consulted.

`locations.yml` has no shipped file: its missing keys always take the built-in values.

## Texts

Every text is a [JSON text component](https://minecraft.wiki/w/Text_component_format) in single
quotes, for example `'{"text":"Press \"t\" for help."}'`. Colours, `clickEvent` and `hoverEvent` work.
Text that isn't valid JSON is shown as plain text, with an error in the log.

Placeholders:

| Placeholder | Where | Replaced by |
|-------------|-------|-------------|
| `<supported_versions>` | the seven `message…` keys of `secondRoom` | the `supportedVersions` list, joined with ", " |
| `<server_version>` | `displayEntity.text` in `locations.yml` | the server's Minecraft version |
| `_@p_` | `introductionChain.broadcastMessage` | the player's name. **Required**: the broadcast fails without it |

## config.yml

### Top level

| Key | If missing | Meaning |
|-----|------------|---------|
| `supportedVersions` | *shipped*: `['26.2', '26.3']` | Client versions that skip the compatibility room (vanilla clients, and Fabric with MCME's marker mod). A version covers every release that shares its protocol: `26.1.2` also covers 26.1 and 26.1.1. Quote the versions. An empty list, `[]`, supports only the server's own version. |
| `joinDelay` | `10` | Ticks after a join before a room, the tips or the reminders start for the player. |
| `roomEnterMoveDelay` | `10` | Ticks after entering a room before the step to the left counts. A room's own `roomEnterMoveDelay` wins. |
| `reminderPeriod` | `600` | Ticks between two reminders of the compatibility room. |
| `reminderDuration` | `100` | Ticks a reminder's warning screen stays, counted from the moment the resource pack is loaded. |
| `rpMessages.*` | see below | Resource-pack messages for all rooms. A room can override each key. |
| `flowLog.enabled` | `true` | Whether the plugin writes the flow log, a line for each step a player takes through the rooms, in `flow/` in its folder. [flow-log.md](flow-log.md) describes it. |
| `flowLog.keepDays` | `90` | Days of flow log to keep; older days are deleted. `0` keeps them all. |

### Resource-pack messages

These keys go under `rpMessages`, or under a room to override them for that room. A missing or empty
specific message falls back to `messageRpWarning`. When that is missing or empty too, the plugin sends
a built-in English text, as it does for a missing `messageRpTimeout`.

| Key | Sent when |
|-----|-----------|
| `messageRpWarning` | the pack failed and no more specific message below is set (`messageRpFailed` is read when this is missing) |
| `messageRpDeclined` | the player declined the pack |
| `messageRpFailedDownload` | the download failed |
| `messageRpFailedReload` | the client failed to apply the pack |
| `messageRpInvalidUrl` | the pack URL was invalid |
| `messageRpTimeout` | the pack hasn't loaded after about 250 seconds |

### Both rooms: `firstRoom` and `secondRoom`

| Key | If missing | Meaning |
|-----|------------|---------|
| `tpTransitionTimes` | *shipped*: `'10 2 10'` | The fade between rooms: `"in stay out"` in ticks. The player moves after `in` ticks. |
| `tpTransitionComponent` | empty | The fade's title text. MCME's pack draws a black screen for `{"text":"\uE01f","font":"fade_black"}`. |
| `messageActionBar` | empty | Action bar text, repeated while the player is in the room. |
| `actionBarDelay` | `40` | Ticks before the first action bar text. |
| `actionBarPeriod` | `40` | Ticks between action bar texts. |
| `advancementKey` | `mcme:intro` | Key of the advancement toast shown on entering the room. Each room needs its own key. |
| `advancementDisplay` | none | The toast's `display` object, as in an advancement JSON (`icon`, `title`, `description`, …). Without it, or with one that isn't valid (a warning in the log names its key), the room shows no toast. The advancement is taken back after 10 seconds, so the toast can show again. |
| `unsilenceDelay` | `20` | Ticks after leaving the rooms before chat comes back. |
| `roomEnterMoveDelay` | the top-level value | See the top level. |
| `canIgnore` | `false` | **Has no effect in this version.** A player can always leave a room by stepping left; leaving the compatibility room that way, without confirming, brings the reminders. |

### `firstRoom` only

| Key | If missing | Meaning |
|-----|------------|---------|
| `cameraOverlay` | *shipped*: `minecraft:gui/overlay/welcome_screen` | The welcome screen: a texture in the resource pack, as a namespaced key without `textures/` and `.png`. |
| `messageWelcome` | empty | Chat text shown with the welcome screen. |

### `secondRoom` only

The compatibility room picks its screen and its text by client:

| Client | Screen (key) | Text |
|--------|--------------|------|
| vanilla, version not supported | `overlayWarningVersion` | `messageUnsupportedVanilla` + `messageConfirmIgnore` |
| Forge, NeoForge, LiteLoader or OptiFine | `overlayWarningModded` | `messageUnsupportedModded` (only on an unsupported version) + `messageShaders` + `messageOptifine` |
| Fabric with MCME's marker mod, version not supported | `overlayWarningMcme` | `messageRunInstaller` |
| Fabric without the marker mod | `overlayWarningMissingMod` | `messageUnsupportedModded` (only on an unsupported version) + `messageShaders` + `messageManualMods` + `messageConfirmIgnore` |

- **The four `overlayWarning*` keys:** if missing, *shipped* (`minecraft:gui/overlay/warning_game_version`,
  `…/warning_modded`, `…/warning_mcme`, `…/warning_missing_mod`). They're textures, in the same form as
  `cameraOverlay`.
- **The seven messages:** empty if missing. Each may use `<supported_versions>`. The installer links
  in the shipped messages open `https://github.com/MCME/mcme-installer/releases/latest`, which always
  shows the newest installer.

### `introductionChain`: the tips after the rooms

| Key | If missing | Meaning |
|-----|------------|---------|
| `title.title`, `title.subtitle` | *shipped*: "Welcome" / "to Minecraft Middle-earth" | The title shown when the rooms end. |
| `title.fadeIn`, `title.stay`, `title.fadeOut` | *shipped*: `1`, `4`, `1` | The title's times, **in seconds**. A `stay` of `0` means 5. |
| `startMessage` | *shipped*: MCME's welcome text | Chat text sent when the title has ended. |
| `broadcastMessage` | `" "` (which fails: no `_@p_`) | Sent `startBroadcastDelay` ticks after the start, in legacy `§` colour codes, with `_@p_` for the player's name. The first time a player finishes, it goes to the whole network through MCME-Connect; after that only to the player. |
| `startBroadcastDelay` | `200` | Ticks after the start before the broadcast. |
| `initialDelay` | `100` | Ticks after the start before the first tip. |
| `period` | `300` | Ticks between tips. |
| `advancementDisplays` | *shipped*: seven tips | One advancement `display` object per tip, shown in order as toasts. A display that isn't valid is left out, with a warning in the log that names its key (`mcme:introchain<number>`, counting from 0). Each tip's advancement is taken back after 10 seconds; how long the toast stays on screen is up to the client. |

### `database`: players' `/confirm` choices

| Key | If missing | Meaning |
|-----|------------|---------|
| `enabled` | `false` | Store choices in MySQL/MariaDB. Without it, each server keeps them in memory until it restarts. |
| `host`, `port` | `localhost`, `3306` | The database server. |
| `database` | `minecraft` | The database (schema); the plugin creates its table `confirm_data` there. |
| `user`, `password` | `root`, empty | The login. |

The connection uses `useSSL=false&autoReconnect=true&connectTimeout=5000&socketTimeout=5000`: a
player's login waits for their row, at most about 5 seconds each for connecting and for the answer.
These settings are read once at startup.

## locations.yml

```yaml
firstRoom:
  world: world
  pos1: -4096 -39 -4378
  pos2: -4094 -35 -4375
  playerLocation: -4095 -38 -4377 90 0
  tpTarget:
    pos: -4099 -38 -4379 180 0
  displayEntity:
    pos: -4095 -38 -4377 180 0
    text: '{"text":"Welcome to Middle-earth!"}'
secondRoom:
  world: world
  pos1: -4101 -39 -4381
  pos2: -4098 -35 -4377
  playerLocation: -4100 -38 -4379 90 0
  tpTarget:
    pos: -4099 -39 -4372 270 0
itemGlow:
  world: world
  pos1: {x: -4100, y: -50, z: -4400}
  pos2: {x: -4000, y: -30, z: -4300}
  items: [BOOK, MAP]
  glowColor: GOLD
```

(The coordinates are an example, not the hub's real layout.)

Positions are `"x y z"` or `"x y z yaw pitch"`.

### `firstRoom` and `secondRoom`

| Key | Meaning |
|-----|---------|
| `world` | The world the room is in. Without it the room doesn't exist. |
| `pos1`, `pos2` | Two opposite corners of the room's box; a player strictly inside it is in the room. |
| `playerLocation` | Where a player is put on entering the room. Without it, that is `0 0 0` in the room's world. |
| `tpTarget` | Where a player goes on leaving the room: a section with `pos` and an optional `world` (default: the room's world), or a position string in the room's world. For the welcome room that is normally inside the compatibility room; for the compatibility room, the hub. |
| `displayEntity` | Welcome room only, optional: a text display spawned in the room's world at `pos`, with the JSON `text` (`<server_version>` allowed). It is not saved with the world, and is removed and spawned again on `/intro reload`. |

### `itemGlow`: glowing items

A player standing in the area who looks at an item frame holding one of the listed items sees it
glow. This works only for players turned on with `/intro glowitem <player> on`. Without an `itemGlow`
section nothing glows.

| Key | If missing | Meaning |
|-----|------------|---------|
| `world` | `hub` | The area's world. |
| `pos1`, `pos2` | `-4100 -50 -4400` and `-4000 -30 -4300` | Two opposite corners of the area where the player must stand, each with `x`, `y` and `z`. |
| `items` | none | Material names of the items that glow, for example `BOOK`. |
| `glowColor` | `WHITE` | The glow's colour, a Minecraft colour name such as `GOLD` or `AQUA`. |
| `rayRange` | `6` | How far the player's look reaches, in blocks. |
| `rayWidth` | `1.5` | How wide the look is, in blocks. |
| `rayRepetitions` | `2` | How many item frames one look can light up. |
| `zoom` | `0.01` | How much larger the glowing copy is than the item in the frame. |
