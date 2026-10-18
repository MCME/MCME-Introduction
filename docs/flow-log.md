# The flow log

The flow log records each step a player takes through the rooms: entering a room, the resource
pack's result, leaving a room and how, being passed over a room, finishing, reminders and
`/confirm`. It is written for tools that show how players get through the introduction, such as
hub-flow, which until now could only see where a player stood last.

Since 1.4.0. `flowLog` in `config.yml` turns it off or sets how long it is kept (see
[configuration.md](configuration.md)).

## Files

- `plugins/MCME-Introduction/flow/YYYY-MM-DD.jsonl`, one file per day, by the UTC date.
- One JSON object per line, UTF-8, each line ending in a line feed.
- Lines are appended one at a time by a background thread. A reader can read a file at any time;
  only the last line can be incomplete while it is being written, so **skip a last line that does
  not parse**.
- Within a file the lines are in time order.
- Files of days older than `flowLog.keepDays` (90) are deleted, at the first event of each day.
  Only files named like `2026-10-18.jsonl` are touched.
- Each server with the plugin writes its own log. Only the server with the rooms (the hub) records
  rooms; the others record only `start`, `stop`, `reminder` and `confirm`.
- Players appear by UUID only, never by name.

## Changes to this format

Events and fields may be added in later versions: **ignore what you do not know.** A field keeps its
meaning under its name; a changed meaning gets a new name.

## Fields of every event

| Field | Meaning |
|-------|---------|
| `time` | When it happened, in UTC, ISO 8601 with milliseconds: `2026-10-18T21:24:59.550Z`. A time on a whole second has no fraction: `2026-10-18T21:25:00Z`. |
| `event` | The event's name, below. |
| `uuid` | The player's UUID. Not on `start` and `stop`. |

## Events

| Event | When | Fields |
|-------|------|--------|
| `start` | The plugin started, or `/intro reload` loaded the settings again | `plugin`, `supportedVersions` |
| `stop` | The plugin stopped: a server shutdown, or `/intro reload`. Every player in a room gets an `exit` with `how: stopped` just before. | |
| `enter` | The player was put in a room: on joining in it, on walking on into it, at the first move in it after `/intro reload` | `room`, the client fields, `warning` |
| `skip` | The player was passed over a room without being put in it | `room`, the client fields, `compatible` |
| `pack` | What came of the resource pack a room waits for to show its screen | `room`, `result`, `waitMs` |
| `exit` | The player left a room | `room`, `how`, `stayedMs` |
| `finish` | The player is through the rooms: the welcome title and the tips start | `first` |
| `reminder` | The compatibility warning was shown again, outside the rooms | `warning` |
| `confirm` | The player used `/confirm` | `choice`, `inRoom` |

### Event fields

| Field | Events | Meaning |
|-------|--------|---------|
| `plugin` | `start` | The plugin's version, such as `1.4.0`. |
| `supportedVersions` | `start` | The client versions that need no compatibility room, as the plugin uses them: `supportedVersions` in `config.yml`, or the server's own version when that list is empty. |
| `room` | `enter`, `skip`, `pack`, `exit` | `welcome` (the first room) or `compatibility` (the second). These are hub-flow's names. |
| `warning` | `enter` (`compatibility` only), `reminder` | Which warning screen the player gets: `version` (a vanilla client on a version that is not supported), `modded` (Forge, NeoForge, LiteLoader or OptiFine), `mcme` (Fabric with MCME's marker mod, on a version that is not supported), `missing_mod` (Fabric without the marker mod). |
| `compatible` | `skip` | `true`: the client needs nothing (a supported version, and vanilla or Fabric with the marker mod). `false`: it would get the room, but the player confirmed before (`/confirm optifine` or `/confirm ignore`). |
| `result` | `pack` | See [pack results](#pack-results). |
| `waitMs` | `pack` | Milliseconds from entering the room to this result. |
| `how` | `exit` | See [ways out of a room](#ways-out-of-a-room). |
| `stayedMs` | `exit` | Milliseconds from the room's `enter` to leaving it. For `walked`, `confirmed` and `skipped` this includes the black screen before the player moves on: half a second with the shipped `tpTransitionTimes`. |
| `first` | `finish` | Whether it is the player's first time through. Only then does the network get the "just joined" broadcast. |
| `choice` | `confirm` | `optifine`, `ignore` or `reset`. |
| `inRoom` | `confirm` | Whether the player was in one of the rooms. `optifine` and `ignore` in the compatibility room take the player out of it (an `exit` with `how: confirmed` follows); anywhere else they only record the choice, which ends the reminders. |

### The client fields

On `enter` and `skip`:

| Field | Meaning |
|-------|---------|
| `version` | The client's Minecraft version, as ViaVersion names its protocol: `26.2`, or `26.1-26.1.2` for releases that share a protocol. |
| `protocol` | The client's protocol number, such as `776`: the number Plan stores. |
| `brand` | The client brand as the client reports it, such as `vanilla`, `fabric` or `forge`. `null` if the client sent none. |
| `marker` | Whether MCME-Architect detected MCME's marker mod, which MCME's installer puts in its Fabric profile. |
| `supported` | Whether `version` is in `supportedVersions`. |

### Pack results

A room shows its screen only once the resource pack has loaded. Rooms record their pack; reminders
do not.

| `result` | Meaning | Final? |
|----------|---------|--------|
| `loaded` | The pack is loaded and the screen is shown. | yes |
| `not_sent` | 10 seconds on, no pack has been offered: the room's text was sent without the screen. The room keeps waiting, so a later `pack` event can follow. | no |
| `declined` | The player declined the pack. | yes |
| `failed_download` | The download failed. | yes |
| `failed_reload` | The client failed to apply the pack. | yes |
| `invalid_url` | The pack's URL was invalid. | yes |
| `timeout` | No result after about 250 seconds. | yes |

A player who leaves the room before a final result gets no further `pack` event for it. A player who
enters a room while a reminder's screen is still on (within `reminderDuration` of a reminder) gets
no `pack` event for that visit.

### Ways out of a room

| `how` | Meaning |
|-------|---------|
| `walked` | Stepped left, to walk on. Out of the compatibility room without confirming, this brings the reminders. |
| `confirmed` | `/confirm optifine` or `/confirm ignore` in the compatibility room. |
| `skipped` | Sent on without stepping left, because the room was no longer needed: the client needs nothing now, or a confirmation arrived, such as one loaded from the database. |
| `quit` | Left the server. |
| `left` | Got out of the room another way, such as a teleport by staff. |
| `stopped` | The plugin stopped: a server shutdown, or `/intro reload`. |

## Reading the log

- **Count players, not events.** One player can have many events of one kind: an `enter` for each
  session that starts in a room, a `reminder` every `reminderPeriod`.
- **Events are in the order things happened**, not in the order of a story. Stepping left records
  the `skip` of a room passed over at once, and the `exit` after the black screen; a `confirm` comes
  before the `exit` it causes.
- **Nothing follows a player's `quit`** until they join again. A player who quits during the black
  screen stays in the room they stepped out of: their `exit` is the `quit`, and they come back to that
  room.
- **Two `start` events without a `stop` between them** mean the server stopped without a clean
  shutdown: the players who were in a room then have no `exit`.
- **Where a player gave up**: their last `exit` with `how: quit`, and its `room`. The `enter` before it
  says which client they had and, in the compatibility room, which warning they saw; the `pack`
  events in between say whether they ever saw the screen.
- **Returning players**: a `skip` of `compatibility` with `compatible: true` for a player whose earlier
  `enter` of that room had `supported: false` is a player who came back with a fixed client.

### Example

A newcomer with a 1.21.4 client, who reads the warning and quits:

```json
{"time":"2026-10-18T18:02:11.204Z","event":"enter","uuid":"4b6f1c2e-9a3d-4e5f-8a7b-6c5d4e3f2a1b","room":"welcome","version":"1.21.4","protocol":769,"brand":"vanilla","marker":false,"supported":false}
{"time":"2026-10-18T18:02:14.731Z","event":"pack","uuid":"4b6f1c2e-9a3d-4e5f-8a7b-6c5d4e3f2a1b","room":"welcome","result":"loaded","waitMs":3527}
{"time":"2026-10-18T18:02:39.950Z","event":"exit","uuid":"4b6f1c2e-9a3d-4e5f-8a7b-6c5d4e3f2a1b","room":"welcome","how":"walked","stayedMs":28746}
{"time":"2026-10-18T18:02:39.951Z","event":"enter","uuid":"4b6f1c2e-9a3d-4e5f-8a7b-6c5d4e3f2a1b","room":"compatibility","version":"1.21.4","protocol":769,"brand":"vanilla","marker":false,"supported":false,"warning":"version"}
{"time":"2026-10-18T18:02:40.003Z","event":"pack","uuid":"4b6f1c2e-9a3d-4e5f-8a7b-6c5d4e3f2a1b","room":"compatibility","result":"loaded","waitMs":52}
{"time":"2026-10-18T18:03:52.018Z","event":"exit","uuid":"4b6f1c2e-9a3d-4e5f-8a7b-6c5d4e3f2a1b","room":"compatibility","how":"quit","stayedMs":72067}
```
