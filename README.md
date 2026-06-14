# BananaPaintball

Red vs blue, team-lives paintball for BananaMC. Built for **Paper 1.21.11** on
**Java 21**. Players throw snowball "paintballs" for one-hit eliminations; each
hit (or arena escape) drains a shared team life pool, and the first team to run
out loses.

This is a modernized, MIT-licensed reimagining of Ajneb97's PaintballBattle.
Hats, perks, killcoins, killstreaks, shops, and internal currency are removed.
Capture the Flag is reserved for a future phase (arenas already store
`mode: TEAM_LIVES` for forward compatibility).

---

## Features

- Red/blue team-lives gameplay with automatic team balancing
- Global "Quick Play" queue and per-map queues (map queue fills first)
- Compact 27-slot GUI menu (Quick Play, Map Select, Statistics, Leaderboards)
- Optional hotbar lobby item that opens the same menu on right-click
- Manual arena setup with in-world commands and live validation
- Arena data persisted to `arenas.yml` (regions, spawns, settings, main lobby)
- Axis-aligned cuboid regions (no WorldEdit dependency) with particle outline preview
- One-hit eliminations with a per-throw cooldown to prevent spam
- On a hit, the victim's team loses one life and both combatants reset to spawn
- Dyed leather armor and snowball loadout handed out at match start
- Snowball replenishment and spawn protection (immune to hits while active)
- Boundary enforcement: leaving the horizontal footprint costs a team life
- Broad arena protection (build, items, containers, fire, fluids, explosions, escapes)
- WorldGuard-friendly: in-match hits never trigger region "cannot PvP here" messages
- Jump-friendly boundary that only fences the horizontal footprint (plus a void floor)
- Sound effects for hits, lost lives, wins, and losses
- Per-player scoreboard sidebar and action-bar updates
- Async MySQL/MariaDB statistics via HikariCP with indexed leaderboards
- PlaceholderAPI player-stat and ranked-leaderboard placeholders
- Global and per-arena winner/loser/tie reward commands
- Optionally disable PaintBall in specific worlds
- Adventure MiniMessage messages with legacy `&` color code support

---

## Requirements

- Paper **1.21.11** (or a compatible 1.21.x patch)
- Java **21**
- MySQL or MariaDB (optional but recommended for statistics)
- PlaceholderAPI (optional, for placeholders)

---

## Installation

1. Drop `BananaPaintball-1.0.0.jar` into your server's `plugins/` folder.
2. Start the server once to generate `config.yml`.
3. Configure the database in `config.yml` (or set `database.enabled: false`).
4. Restart the server.
5. Set the main lobby and build at least one arena (see below).

The PaintBall server is intended to be **dedicated** to this minigame. Players
arrive in the main lobby and choose Quick Play or a specific map via the menu,
commands, or the optional lobby item.

### Generated files

| File | Purpose |
|------|---------|
| `config.yml` | Database, equipment, sounds, scoreboard, rewards, messages |
| `arenas.yml` | Main lobby location and all arena definitions |

---

## Building from source

```bash
./gradlew shadowJar
# Output: build/libs/BananaPaintball-1.0.0.jar
```

HikariCP is shaded and relocated under `com.bananamc.paintball.libs.hikari`.

---

## Quick start: creating an arena

Build your map manually, then stand at each required location and run:

```text
/paintball setmainlobby
/paintball arena create warehouse
/paintball arena warehouse setwaiting
/paintball arena warehouse setspawn red
/paintball arena warehouse setspawn blue
/paintball arena warehouse setpos1
/paintball arena warehouse setpos2
/paintball arena warehouse setminplayers 4
/paintball arena warehouse setmaxplayers 12
/paintball arena warehouse setlives 100
/paintball arena warehouse setduration 360
/paintball arena warehouse setcountdown 20
/paintball arena warehouse setcooldown 1.0
/paintball arena warehouse enable
```

Rules enforced by validation:

- `pos1` and `pos2` define the playable cuboid; both must be in the same world.
- Red and blue spawns must be inside the region and in the same world as it.
- The waiting lobby may be outside the region but must be set.
- Minimum players must be at least 2; maximum must be greater than or equal to minimum.
- Lives, duration, and countdown must all be greater than 0.
- An arena cannot be enabled until validation passes.
- `/paintball arena <name> info` reports current settings and validation failures.
- `/paintball arena <name> visualize` outlines the region with particles for ~10s.

Arena names are case-insensitive for lookup and commands.

---

## Commands

### Players (`paintball.play`, default: true)

| Command | Description |
|---------|-------------|
| `/paintball` | Open the PaintBall menu |
| `/paintball queue` | Join the global Quick Play queue |
| `/paintball join <arena>` | Join a specific map queue |
| `/paintball leave` | Leave the queue or current match |
| `/paintball status` | Show your queue/match status |
| `/paintball stats [player]` | Show statistics (online or offline) |
| `/paintball top <wins\|kills>` | Show a leaderboard in chat |

Alias: `/pb`

Tab completion is available for subcommands, arena names, and leaderboard types.

### Admins (`paintball.admin`, default: op)

| Command | Description |
|---------|-------------|
| `/paintball setmainlobby` | Set the main lobby at your location (saved to `arenas.yml`) |
| `/paintball arena create <name>` | Create a new arena |
| `/paintball arena <name> setwaiting` | Set the waiting lobby |
| `/paintball arena <name> setspawn <red\|blue>` | Set a team spawn |
| `/paintball arena <name> setpos1` / `setpos2` | Set region corners |
| `/paintball arena <name> setminplayers <n>` | Minimum players |
| `/paintball arena <name> setmaxplayers <n>` | Maximum players |
| `/paintball arena <name> setlives <n>` | Shared team lives |
| `/paintball arena <name> setduration <seconds>` | Match length |
| `/paintball arena <name> setcountdown <seconds>` | Pre-match countdown |
| `/paintball arena <name> setcooldown <seconds>` | Per-arena throw cooldown (decimals allowed) |
| `/paintball arena <name> enable` / `disable` | Toggle availability |
| `/paintball arena <name> info` | Show settings and validation |
| `/paintball arena <name> visualize` | Outline the region |
| `/paintball arena <name> remove` | Delete the arena |
| `/paintball reload` | Reload `config.yml` and `arenas.yml` |

Disabling or removing an arena force-stops any active match in it.

---

## Configuration

Key sections in `config.yml`:

### `database`

MySQL/MariaDB connection settings: `enabled`, `host`, `port`, `database`,
`username`, `password`, `pool-size`, `use-ssl`, `table-prefix` (default `pb_`).
If disabled or unreachable, matches still run but stats are not persisted.

### `disabled-worlds`

List of world names (case-insensitive) where player features are blocked:
the `/paintball` menu, queue commands, and lobby item. Admin setup commands
still work in these worlds.

### `lobby-item`

Hotbar item given on join (and after matches when enabled). Defaults to
`enabled: false` — turn it on for hub-style access without typing commands.
Fields: `material`, `slot`, `name`, `lore`.

### `equipment`

| Key | Default | Description |
|-----|---------|-------------|
| `snowball-amount` | 16 | Snowballs given at match start and refilled up to this count |
| `snowball-refill-seconds` | 2 | How often missing snowballs are topped up |
| `spawn-protection-seconds` | 3 | Seconds of hit immunity after spawning |
| `boundary-cooldown-seconds` | 2 | Anti-double-count window after a boundary exit |
| `throw-cooldown-seconds` | 1.0 | Default per-throw cooldown for new arenas (decimals allowed) |
| `boundary-floor-buffer` | 5.0 | Blocks below the region floor before a player counts as out of bounds |

Per-arena throw cooldown is set with `/paintball arena <name> setcooldown` and
overrides the global default for that map.

### `sounds`

Gameplay sound effects using namespaced Minecraft sound keys (`minecraft:...`).
Set `enabled: false` to silence all sounds, or leave individual keys blank to
skip them. Keys: `hit`, `life-lost`, `countdown` (10s and 5s),
`countdown-final` (3, 2, 1 — falls back to `countdown` if blank), `win`, `lose`.

### `scoreboard`

`enabled`, `title`, and `lines` for the in-match sidebar. The menu title also
uses `scoreboard.title`.

### `rewards` and `arena-rewards`

Console command lists run at match end for each participant.

- `rewards.winner` / `rewards.loser` / `rewards.tie` — global defaults.
- `arena-rewards.<arena>.winner` / `loser` / `tie` — per-arena overrides that
  fully replace the global lists for that map. Arena names are case-sensitive
  and must match how the arena was created.

Reward placeholders: `%player%`, `%uuid%`, `%arena%`, `%kills%`, `%result%`
(`WIN`, `LOSS`, or `TIE`).

### `messages`

All player-facing strings. Supports MiniMessage tags and legacy `&` codes.
Notable keys include queue/match flow (`joined-queue`, `match-start`,
`match-win`, `throw-cooldown`, `boundary-warning`, `returned-to-lobby`) and
admin feedback (`no-permission`, `world-disabled`).

### Scoreboard / message placeholders

`%arena%`, `%red_lives%`, `%blue_lives%`, `%kills%`, `%time%` (formatted as
`m:ss`).

---

## PlaceholderAPI

Identifier: `paintball`

Player stats (resolved from the online cache, or empty defaults):

- `%paintball_wins%`
- `%paintball_losses%`
- `%paintball_ties%`
- `%paintball_kills%`
- `%paintball_matches%`

Leaderboards (`<rank>` starts at 1; cached and refreshed every 60 seconds):

- `%paintball_top_wins_name_<rank>%` / `%paintball_top_wins_value_<rank>%`
- `%paintball_top_kills_name_<rank>%` / `%paintball_top_kills_value_<rank>%`

Empty ranks return `---` for names and `0` for values.

---

## FancyNpcs (hub transfer)

BananaPaintball does not require a FancyNpcs API dependency. Transfers happen at
the proxy/NPC level:

```text
/npc action <npc_id> RIGHT_CLICK add send_to_server <paintball_server>
```

Enable `bungee-plugin-message-channel = true` in Velocity, configure an
interaction cooldown, and ensure the server name matches your Velocity config.

---

## Gameplay notes

### Queuing and matchmaking

- Matchmaking runs automatically: when an arena is free and enough players are
  queued, a match starts.
- Map-specific queues are filled first; remaining slots are taken from the
  global Quick Play queue up to the arena's maximum.
- If the pre-match countdown drops below the minimum player count, it is
  cancelled and everyone is restored and returned to their original queue.
- Leaving a queue via command or the menu's **Leave Queue** button removes you
  without penalty.

### During a match

- Players receive dyed leather armor and snowballs. Only snowballs may be thrown;
  other projectiles are blocked. Hunger is locked and all damage is cancelled.
- Paintballs are snowballs. Each throw is rate-limited by the arena's throw
  cooldown; throwing while on cooldown is blocked, shows an action-bar warning,
  and does not consume a snowball.
- The first enemy hit on a player without spawn protection deducts one life from
  that player's team. Both the victim and the shooter (if on the enemy team)
  reset to their team spawns with fresh spawn protection. Only the victim's
  team loses the life; kill credit goes to the shooter.
- Eliminations are broadcast on the action bar. Friendly-fire hits still cost
  the victim's team a life but do not grant kill credit.
- Leaving the arena's horizontal footprint (with a floor buffer for jumping/falling)
  costs one team life, shows a boundary warning, and respawns you at your team
  spawn. A short cooldown prevents the same exit from deducting twice.
- Ender pearls, chorus fruit, and dimension portals are blocked during matches.
- Mid-match `/paintball leave` restores your pre-match state and deducts one
  life from your team. Disconnecting removes you without restoration but still
  deducts a life.

### After a match

- When a match ends, each player's inventory, armor, game mode, health, hunger,
  XP, and flight state are restored to what they were when the match was found.
  They are teleported back to that pre-match location (not forcibly sent to the
  main lobby). The lobby item is re-given when `lobby-item.enabled` is true.
- A match ends when a team reaches zero lives or time expires. On timeout the
  team with more remaining lives wins; equal lives is a tie.
- Simultaneous matches in separate arenas are fully isolated, including
  cross-arena projectiles.

---

## Statistics storage

Player stats (UUID, latest name, wins, losses, ties, kills, matches) are stored
asynchronously in MySQL/MariaDB using a HikariCP connection pool. Match results
are written atomically, and leaderboards use indexed queries. Stats are loaded
into memory when a player joins and unloaded on quit. If the database is
disabled or unreachable, the minigame still runs without persistence.

---

## License

MIT. Based on Ajneb97's MIT-licensed PaintballBattle.
