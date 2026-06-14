# BananaPaintball 1.21.11

## Summary

Modernize Ajneb97's MIT-licensed PaintballBattle for BananaMC using Paper
1.21.11 and Java 21. Create `BananaPaintball` under
`com.bananamc.paintball`.

Preserve red-versus-blue team-lives gameplay, statistics, and leaderboards.
Remove hats, perks, killcoins, killstreaks, shops, and internal currency.
Capture the Flag remains a later phase.

## Matchmaking and UI

- FancyNpcs transfers players from the hub directly to the dedicated
  PaintBall server through Velocity.
- Arriving players enter the PaintBall lobby and receive a configurable
  hotbar item that opens the PaintBall menu.
- `/paintball` opens the same compact 27-slot GUI.
- The menu provides:
  - **Quick Play:** Join the global queue for the first suitable arena.
  - **Map Select:** Open a compact list of enabled arenas.
  - **Statistics:** Show personal wins, losses, ties, kills, and matches.
  - **Leaderboards:** Show top wins and kills.
  - **Leave Queue:** Replace queue actions while the player is queued.
- Selecting a map places the player in that map's dedicated queue until it
  is available or the player leaves.
- Provide `/paintball queue`, `/paintball join <arena>`,
  `/paintball leave`, `/paintball status`, `/paintball stats [player]`,
  and `/paintball top <wins|kills>`.
- Countdown cancellation returns players to their original global or
  map-specific queue.

## Arena Creation

Administrators build each map manually and configure it while standing at
the required locations:

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
/paintball arena warehouse enable
```

- `pos1` and `pos2` define an axis-aligned playable cuboid without
  requiring WorldEdit.
- Both positions and team spawns must be in the same world.
- Team spawns must be inside the region. The waiting lobby may be outside.
- Arenas store `mode: TEAM_LIVES` for future CTF compatibility.
- `/paintball arena <name> info` reports settings and validation failures.
- `/paintball arena <name> visualize` temporarily outlines the region with
  particles.
- An arena cannot be enabled until configuration validation succeeds.

## Gameplay and Protection

- Assign queued players to suitable idle arenas and automatically balance
  red and blue teams.
- Preserve one-hit paintball eliminations, team equipment, snowball
  replenishment, spawn protection, arena chat, scoreboards, and shared
  team lives.
- Leaving the arena region counts as one elimination, deducts one team
  life, and respawns the player at the team spawn.
- Use a boundary cooldown to prevent duplicate life deductions.
- End a match when one team reaches zero lives or time expires. Remaining
  lives determine timeout results.
- Return players to the admin-defined PaintBall main lobby after matches,
  leaves, interruptions, and forced stops.
- During countdowns and matches, block building, breaking, item movement,
  containers, explosions, fire, fluids, outside damage, portal, pearl, and
  chorus-fruit escapes, and cross-arena projectiles.
- Terrain cloning and restoration are excluded because gameplay cannot
  modify arena blocks.

## Visual Design

Use the BananaMC visual language from the supplied reference:

- Lime green for branding, headings, and active indicators.
- White for primary text and values.
- Gray for secondary or inactive information.
- Cyan for informational statistics.
- Red and blue only for warnings and team-specific information.
- Dark neutral inventory backgrounds with restrained decoration.

The compact match sidebar displays BananaMC and PaintBall branding, arena,
red and blue lives, personal kills, and remaining time. Action bars display
temporary queue updates, eliminations, boundary warnings, and respawn
protection.

Use Adventure MiniMessage internally while accepting legacy `&` color
codes. Make messages, colors, scoreboard lines, GUI titles, materials,
slots, and lobby items configurable.

## Statistics and Rewards

- Store UUID, latest name, wins, losses, ties, kills, and matches
  asynchronously in MySQL or MariaDB using HikariCP.
- Update match statistics atomically and provide indexed leaderboard
  queries.
- Expose PlaceholderAPI player-stat and ranked-leaderboard placeholders
  for future holograms.
- Execute configurable winner, loser, and tie console commands using
  `%player%`, `%uuid%`, `%arena%`, `%kills%`, and `%result%`.

## FancyNpcs

Configure the hub NPC with a right-click server-transfer action:

```text
/npc action <npc_id> RIGHT_CLICK add send_to_server <paintball_server>
```

Enable `bungee-plugin-message-channel = true` in Velocity, configure an
interaction cooldown, and ensure the server name matches the Velocity
configuration. BananaPaintball does not require a FancyNpcs API dependency.

## Test Plan

- Test global and map-specific queue ordering, cancellation, arena
  capacity, and unavailable-map waiting.
- Test arena validation, region visualization, boundary eliminations,
  protection, and projectile isolation.
- Test team balancing, life depletion, timeouts, ties, disconnects, forced
  stops, and server shutdown.
- Test every GUI state, command equivalent, lobby item, scoreboard, and
  action-bar message.
- Verify FancyNpcs transfer and arrival behavior through Velocity.
- Test MySQL schema creation, atomic updates, reconnect handling, and
  leaderboard ordering.
- Run simultaneous matches in separate arenas and verify complete
  isolation.
- Confirm all removed progression and currency systems are inaccessible.

## Assumptions

- The PaintBall server is dedicated to this minigame.
- Players arrive in its main lobby and choose Quick Play or a map rather
  than being immediately queued.
- Arena maps and obstacles are built manually.
- Arena regions are axis-aligned cuboids.
- Players return to the PaintBall main lobby after matches.
- Parties, map voting, WorldEdit integration, terrain restoration, and CTF
  are excluded from v1.
