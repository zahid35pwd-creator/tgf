# Build Spec — an original match-3 saga game

Derived from the teardown in `TEARDOWN.md`. This describes how to build an
*original* game with the same class of systems. Art, audio, characters, naming
and level content must be your own; everything here is mechanics and architecture.

---

## 0. The decisions that matter most

| Decision | What the shipped game does | Recommendation |
|---|---|---|
| Board size | 9 × 9 for all 23,885 levels | **Lock 9 × 9.** Fixed geometry makes layout, difficulty tuning, hints and art all cheaper. Express variety with holes (`000`), not with size. |
| Colours | 2–6 per level, mode 5 | 6 colour assets, per-level `colourCount` + `allowedColours` |
| Move budget | 14–37, mode 15 | 15–35 |
| Simulation | Deterministic, seeded, replayable | **Non-negotiable.** Everything downstream (anti-cheat, QA bots, bug repro) needs it |
| Logic/render split | Logic is a pure state machine; views observe | **Non-negotiable.** Ship headless |
| Content delivery | 79 packages, OTA crates with semver | Start with one bundle, design the manifest for OTA from day one |
| Tuning | Two flat text files (`scoreconfig.prop`, `tweakdata.prop`) | Copy the idea exactly: all numbers in data, hot-reloadable |

---

## 1. Architecture

```
┌──────────────────────────────────────────────────────────────┐
│ app shell          platform, store, ads, push, consent       │
├──────────────────────────────────────────────────────────────┤
│ meta layer         map, episodes, lives, inventory, quests,  │
│                    events, shop, social, popup queue         │
├──────────────────────────────────────────────────────────────┤
│ presentation       scenes, HUD, timelines, particles, VFX,   │
│                    audio — observers only, zero authority    │
├──────────────────────────────────────────────────────────────┤
│ GAME LOGIC CORE    headless, deterministic, no I/O           │
│                    Board · Rules · GameMode · DestructionPlan│
├──────────────────────────────────────────────────────────────┤
│ engine             ECS, renderer, atlas, audio, scripting    │
└──────────────────────────────────────────────────────────────┘
```

### 1.1 The core is headless and total

The shipped game proves the pattern: `CGameLogic*`, `CGameMode*`,
`IDestructionPlan`, `CBoardAction*` are logic-only, and view classes
(`CJellyJarView`, `CMarmaladeLockView`, `COrlockView`, `CTileView`) subscribe.
A board action never touches a sprite.

```ts
interface GameLogic {
  start(level: LevelDef, seed: number): GameState
  legalMoves(s: GameState): Move[]
  apply(s: GameState, m: Move): { state: GameState; events: GameEvent[] }
  // every visual is driven off `events`; the state is the only truth
}
```

Because `apply` is pure, you get for free: a bot that plays every level 10,000
times for balancing (the shipped game has one — `mGamePolicy.IsBotPlay`),
server-side score revalidation, and replay from `(levelId, seed, moveList)`.

### 1.2 Tick-based choreography, not per-frame logic

Shipped timings are quoted in *ticks* and milliseconds together:
`board.tickstodestroy.default 4`, `matcher.wrapline.tickstodestroy 58`,
`destructionplan.colorbomb.totalticksbase 10`,
`party.booster.pinata.explode.ticks 450`.

Run the logic on a fixed tick (60 Hz) and let the renderer interpolate. Cascades,
line blasts and colour-bomb sweeps are all "N ticks from now", which is what makes
the choreography reproducible and testable.

### 1.3 Destruction plans

Do not remove items inline. Each effect allocates a **plan** that owns an ordered
list of (cell, tick) removals, then executes over time. Mirror the shipped class
set:

```
PlanLine            striped: sweep a row or column outward from origin
PlanCross           striped × striped: both axes
Plan3x3 / Plan5x5   wrapped: two detonations (shipped: 430 ms then 530 ms apart)
PlanColorBomb       pick a colour, remove all of it, staggered
PlanColorBombPair   colour bomb × colour bomb: clear the board
PlanStripedWrapped  wrapped × striped: 3 rows + 3 columns
PlanFish            fish swims to a target cell (weighted — see 2.7)
PlanIngredient      ingredient falls to an exit
PlanSpawnBlocker    chocolate / generator spawning
```

Plans compose: a plan's removals can trigger new matches, which allocate new
plans. Keep one queue, drain it in tick order, and the whole cascade becomes
deterministic.

---

## 2. Core rules

### 2.1 Cell model

A cell is a layer stack, which is exactly what the `tileMap` string encodes.
Use an explicit struct rather than a packed string:

```ts
interface Cell {
  tile: 'none' | 'normal' | 'glass' | 'portal' | 'rapids'
  jelly: 0 | 1 | 2                  // 004 / 003
  occupant: Occupant | null          // candy, special, ingredient, blocker-item
  overlays: Overlay[]                // locks that ride on the occupant
  understate: Understate[]           // layered blockers under the occupant
  spawner?: SpawnTable               // 005 + its table entries
  colourLock?: ColourId[]            // 055-060 / 214-219 semantics
}
```

Three distinct blocker attachment modes, all present in the shipped game — get
these right and most of the blocker catalogue falls out:

1. **Under** the candy, consumed layer by layer by adjacent matches
   (frosting, waffle, chain) — multi-layer, `layers: 1..5`.
2. **On** the candy, immobilising it until broken (licorice lock, honey/marmalade,
   sugar lock, bubblegum) — the candy still counts for colour.
3. **Instead of** the candy, occupying the cell (licorice swirl, chocolate,
   gumball machine, jelly jar, cake bomb, skull, cobra).

### 2.2 Match detection

Horizontal and vertical runs of ≥3 same-colour, movable occupants. Resolve the
whole board at once, merge overlapping runs into one *match group*, then classify:

```
len 3              → clear
len 4 straight     → striped (axis = perpendicular to the run, per CCS convention)
len 5 straight     → colour bomb
L / T (5 cells)    → wrapped
len 6+ or double   → engine-specific; the shipped game has super-line-blast and
                     super-wrapped for these
```

The special is created at the **swapped cell** if it is in the group, otherwise at
the group's intersection, otherwise at the group centre. This rule is what lets
players aim specials, and players notice immediately if it is wrong.

### 2.3 Swap validation and free switch

A swap is legal if it creates ≥1 match, **or** either side is a colour bomb,
**or** both are specials (combo swaps are always legal). The Free Switch booster
lifts the restriction for one move — model it as a flag on the move, not a
separate code path.

### 2.4 Gravity and spawning

Gravity is per-column, downward, with the shipped tuning
(`board.slide.speed 5.5`, `board.max.speed 0.4`, `board.speed.factor 0.032` —
accelerating fall, capped).

Complications you need from the start, because retrofitting them is painful:

- **Spawners anywhere**, not only the top row. Code `005` sits on the topmost
  playable cell of each column, but levels place them mid-board too.
- **Spawn tables** per spawner: a weighted list of what it can emit
  (normal candy, striped, wrapped, colour bomb, fish, ingredient, licorice,
  chocolate, shield, lucky candy), plus optional colour restriction.
- **Diagonal feed** around holes, so candy slides sideways into a gap below.
- **Conveyors, portals and gates** change the fall graph. Represent falling as a
  directed graph over cells, not as `row - 1`.
- **Cannons** inject specific items from the edge on a queue
  (`cannons: [[26],[26],[26,107]]`).

### 2.5 Cascade loop

```
apply swap
loop:
  find matches  → create specials  → allocate plans
  execute plans at their ticks
  resolve blocker damage from adjacent clears
  apply gravity; run spawners
until no matches and the board is stable
then: advance per-move systems (chocolate grows, pepper fuses tick,
      cobra moves, spawner intervals, timed blockers)
```

Only after the board is stable does the **move counter** decrement and per-move
hazards advance. Getting this order wrong is the single most common match-3 bug.

### 2.6 No-moves and shuffle

After every stable board, enumerate legal moves. Zero moves → auto-shuffle
(shipped: 1,300 ms animation, 800 ms delay) preserving the multiset of occupants.
Reshuffle until ≥1 move exists; if a bounded number of attempts fails, inject a
special. Also use the move count for the hint system (§3.2).

### 2.7 Weighted targeting

The shipped game has a family of "weighters" — `CJellyFishWeighter`,
`CSkullFishWeighter`, `CDropDownFishWeighter`, `CSugarKeyFishWeighter`,
`CPatternFishWeighter`, `CCompositeFishWeighter`. A fish does not pick randomly:
each active objective contributes a weight per cell, and the composite picks the
best. Build this as a strategy interface from day one — it is what makes specials
feel smart, and it is the same machinery the hint system wants.

### 2.8 Scoring

Start from the shipped table (`TEARDOWN.md` §6.1) and keep every number in data:

```
base match                 20 per item
jelly cleared            1000 per layer
blocker layer              20
ingredient collected    10000
fish removal              250     striped removal     500
wrapped removal          1000     colour bomb        5000
order grade tiers    100 / 1000 / 5000 / 10000
end-of-level: each leftover move converts to a special  → 6000 each
```

Three star thresholds per level. Note that the shipped targets are not round
numbers (`[3500, 41918, 84710]`) — they are generated from bot playthrough
distributions, not hand-picked. Plan for a target-generation pass.

### 2.9 End-of-level sequence

1. Objective met → freeze input.
2. **Sugar-crush phase**: each leftover move becomes a random special, which
   detonates; score accrues (shipped: 500 ms delay, 800 ms per beat, skippable
   after 10 s).
3. Star evaluation, star-fly-to-HUD, reward reveal, episode progress.
4. Fail path → the end-game offer surface (extra moves, end-game boosters),
   which is where a large share of revenue lives.

---

## 3. Game modes

Implement mode as a **composable objective set**, not a subclass per combination.
The shipped game started with subclasses (`CGameModeJellyOrder`,
`CGameModeOrderIngredients`, `CGameModeRainbowRapidsJelly`, …) and then added
`CGameModeFlexible` with a data-driven `objectives` array. Start where they ended up:

```jsonc
"objectives": [
  { "type": "JELLY" },
  { "type": "ORDER",       "item": "candy_red", "amount": 51 },
  { "type": "INGREDIENTS", "amount": 3 },
  { "type": "SCORE",       "amount": 50000 },
  { "type": "RAPIDS",      "targets": [5,10,9,2,4] }
]
```

A level is won when all objectives are satisfied within `moveLimit`. The four
shipped primitives cover 23,885 levels:

| Objective | Rule |
|---|---|
| `JELLY` | Clear every jelly layer. Single and double. |
| `ORDER` | Collect N of each listed item type (colours, specials, blockers). |
| `INGREDIENTS` | Deliver N ingredients to bottom exits; they obey gravity. |
| `SCORE` | Reach a score (also the 1★ gate for every level). |

Add `RAPIDS` (route candy through a bridge/node graph) once the base is solid —
it needs its own graph model and is where ~2,650 of the shipped levels live.

---

## 4. Level data

### 4.1 Recommended schema

The shipped `tileMap` string encoding is compact but opaque. Author in a readable
schema and compile to a packed form:

```jsonc
{
  "schema": 1,
  "id": 1042,
  "mode": { "objectives": [ { "type": "JELLY" } ] },
  "moveLimit": 22,
  "colourCount": 5,
  "allowedColours": [0,1,2,3,4],
  "scoreTargets": [12000, 24000, 40000],
  "seed": 0,
  "difficulty": 1,                     // 0 normal … 4 legendary
  "tags": ["endurance"],               // analytics / quest targeting only
  "grid": [                            // 9 rows × 9 cols
    "....XXX..",
    "..XXXXXX.",
    ...
  ],
  "layers": {
    "jelly":    [ { "cells": "r2c3-r6c7", "layers": 2 } ],
    "frosting": [ { "cell": [4,4], "layers": 3 } ],
    "licorice": [ [1,1], [1,7] ]
  },
  "spawners": [
    { "cell": [0,3],
      "table": [ { "item": "candy", "weight": 90 },
                 { "item": "striped_row", "weight": 6 },
                 { "item": "wrapped", "weight": 4 } ],
      "colours": [0,1,2] }
  ],
  "features": {
    "portals":  [ { "from": [0,4], "to": [0,6] } ],
    "gates":    [ { "from": [4,8], "to": [4,7], "dir": 0, "type": 1 } ],
    "cannons":  [ { "cell": [0,0], "ammo": ["candy","candy","striped_row"] } ],
    "spreaders":[ { "cell": [4,4], "kind": "chocolate",
                    "amount": 1, "interval": 3 } ]
  }
}
```

Compile to the same storage shape the shipped game uses, which is a good design:
**SQLite, one row per level, JSON blob, uncompressed** — one file, random access
by ordinal, trivially diffable, trivially streamable, and `level_version` lets a
level be hot-patched without an app update.

```sql
CREATE TABLE levels (
  ordinal   INTEGER PRIMARY KEY,
  data      BLOB NOT NULL,
  version   INTEGER NOT NULL,
  hash      TEXT NOT NULL      -- the shipped game exposes get_level_json_hash()
);
```

### 4.2 Content volume reality check

23,885 levels in 1,593 episodes of 15. You will not hand-author that. The pipeline
that makes it possible:

1. **Level editor** that writes the authoring JSON.
2. **Bot** that plays each candidate N thousand times headless.
3. **Target generator** that sets `scoreTargets` from the bot's score distribution
   and `difficulty` from its win rate.
4. **Reject gate**: unwinnable, trivially winnable, or statistically degenerate
   levels never ship.
5. **Regression harness**: every logic change re-runs the bot over the corpus and
   diffs win rates. This is why determinism is non-negotiable.

Ship ~200–500 hand-tuned levels at launch; the corpus grows live via OTA.

### 4.3 Progression shape

```
episode = 15 levels      world/chapter = several episodes
difficulty tiers         normal, hard, super hard, crazy hard, legendary
hard levels              gated, flagged in metadata (hll), drive the hard-level offer
episode end              trophy + gate (stars, tickets, or friend help)
```

---

## 5. Difficulty and retention systems

These are the levers that turn a match-3 into a saga game. All three ship as data
in the original, and all three are A/B-cased.

### 5.1 Static difficulty

Per-level tier 0–4, authored and stored in metadata. Drives UI treatment (the
shipped game has five distinct objective-panel scenes) and offer targeting.

### 5.2 Dynamic difficulty

`[[levelOrdinal, levelDefinitionId, failThreshold], …]` with per-level thresholds
5–43. After `failThreshold` failures on a level, bias the spawner tables toward
helpful outcomes (more specials, better colour distribution). Keep an exclusion
list for levels that must never be eased (tutorials, puzzle levels).

### 5.3 First-attempt challenge

`[{ levelOrdinal, levelDefinitionID, moves }]` — a **reduced** move count on the
player's first attempt at specific levels, restored to normal on retry. It
manufactures a near-miss, which is what sells extra moves. Use it deliberately
and keep it in data so it can be A/B tested and switched off.

### 5.4 Lives

```
max 5          regen 1 life / 1800 s (30 min)
gift from friends every 18 h        send life / send moves every 24 h
unlimited-lives timers as a product (SKU 5)
expanded lives storage as a purchase perk (SKU 63, 89)
```

---

## 6. Meta layer

### 6.1 Inventory and economy

Define one flat item enum; everything is an item — currencies, lives, boosters,
tokens, passes, chests. The shipped enum (`TEARDOWN.md` §7.2) is a good template.
Grant and spend go through one transactional service with an ack protocol
(`AppUserInventoryApi.ackTransactions`), so a crash mid-grant cannot lose or
duplicate items.

Three booster durability classes: consumable, semi-durable (timed unlimited use),
level-durable (unlimited for one level). Plus `END_GAME_*` variants that only
exist on the fail screen.

### 6.2 Popup queue

With dozens of live-ops surfaces competing for the moment after a level ends, you
need a real scheduler, not ad-hoc calls. Mirror the shipped design:

- Named **slots** (map-enter, pre-level, post-level, app-start).
- Priority plus per-slot caps (`-1` = uncapped).
- Whitelist/blacklist by feature or `feature.popup`.
- One flow at a time: "Unable to start flow X while flow Y is already active".

### 6.3 Live-ops framework

Each event is an independent content package with its own `events.json`,
data sources, scenes and server state. Build the *framework* once:

```
event descriptor  → id, window, cohort, config, reward table
state machine     → Intro → Ongoing → Claim → Milestone → Ended
widget            → map surface + event tab card
quest system      → objective types shared across all events
reward service    → grants through the inventory transaction service
```

Then events become content, not code. The shipped game runs 30+ concurrent
surfaces this way (season pass, episode race, weekly race, leagues, chests,
chocolate box, treasure defense, expeditions, prize draw, streaks, piggy bank).

### 6.4 Remote config and A/B

Per-feature flags fetched on an interval (shipped: app settings and A/B case every
7,200 s, user metrics every 36,000 s, pay state every 3,600 s), with offline
holdout support so a cohort can be excluded locally.

---

## 7. Presentation

### 7.1 Rendering

2D orthographic, atlas-batched sprites, SDF text, particle system with a hard
emitter cap (shipped: 512), batched vertices capped per mesh (512) and per frame
(4,096). Skeletal animation for characters. Two texture tiers (SD/HD) chosen by
device, with HD overriding only what benefits.

### 7.2 Feel — the details that sell a match-3

From the shipped tuning, these are the ones players feel:

- Accelerating, capped candy fall; items land with a small overshoot.
- Idle hint pulse after 3 s, animating 2.3 s.
- Move-counter warning at ≤5 moves, pulsing every 400 ms.
- Screen shake on wrapped (430 ms + 530 ms), striped-wrapped, and super crush.
- An **exclamation ladder** of escalating VO/UI callouts tied to cascade depth
  and combo value. The shipped ladder: sweet → tasty → delicious → divine →
  marvelous → magnificent → irresistible → unbelievable → frogtastic →
  sugar-crush. Write your own words; keep the escalation.
- A score popup per clear, 1 s lifetime.
- Audio layering: 4–5 variants of each match/land/switch sound, pitch-stepped by
  cascade depth, so a long cascade rises in pitch.

### 7.3 Audio budget

The shipped mix: 203 in-game SFX, 74 menu SFX, 18 VO lines, 4 bundled music
tracks (one gameplay loop, one map loop, win and lose stingers), everything OGG
Vorbis, with at most 2 music files in memory. That is a realistic target: deep SFX
coverage, shallow music.

### 7.4 UI as data

Scenes, prefabs, atlases, particles and timelines as data files; a binary
serialisation for ship builds; events and data-sources as JSON so UI binds to
state without code. Add a layout-configuration file and a device-resolution test
matrix as data, so notch/fold/tablet handling is testable without devices.

---

## 8. Tooling — build these before level 100

1. **Headless simulator** with a CLI: play a level from a seed and a move list,
   print the state and events. The whole test suite runs against this.
2. **Bot** with pluggable strategies (random, greedy, objective-weighted) for
   balancing and regression.
3. **Level editor** with in-editor simulation and live win-rate estimate.
4. **In-game debug console**, exactly like the shipped one:
   `boardaction hammer/drag/freeswitch/paintbrush`, `morph <type>`,
   `morphpattern <type> <wave|heart|romb|mask>`, `booster <type> <n>`,
   `closefail [n]` (jump to one objective from winning), `hardlevel <id> <tier>`,
   `shuffle`, `countspawnedcolors`, `translate`, `showstringid`.
   This pays for itself within a month.
5. **Replay capture** on crash: `(levelId, seed, moves)` is a few bytes and
   reproduces any board bug exactly.
6. **Breadcrumb ring buffer** (100 entries × 512 bytes) attached to crash reports,
   plus an ANR watchdog (10 s threshold).
7. **Localisation pipeline** from CSV, with a `showstringid` debug mode and
   pseudo-localisation for layout testing. Budget for RTL and for Thai/Arabic/CJK
   font fallback from the start.

---

## 9. Suggested build order

| Phase | Deliverable | Done when |
|---|---|---|
| 1 | Headless core: 9×9 board, 6 colours, match/cascade/gravity/spawn, seeded RNG, legal-move enumeration, shuffle | Unit tests pass; a random bot finishes 10k boards with no stalls |
| 2 | Specials and the full combination matrix as destruction plans | Every pair in §1.3 has a test asserting exact cell sets |
| 3 | Objectives: score, jelly, order, ingredients + star scoring + sugar-crush | 50 hand-made levels playable headless |
| 4 | Renderer, HUD, feel pass, audio | A level is fun to play with placeholder art |
| 5 | Blockers, tier 1: frosting/multi-layer, licorice lock, licorice swirl, honey, chocolate spreader | Layer/attachment model proven against all three modes |
| 6 | Map, episodes, lives, stars, save/load | 100-level saga loop closes |
| 7 | Level editor + bot + target generator | Level throughput exceeds 20/day |
| 8 | Inventory, boosters, shop, popup queue | Pre-level and end-game offer surfaces work |
| 9 | Remote config, A/B, OTA content packages, analytics | A level can be patched without a store release |
| 10 | Live-ops framework + first two events; difficulty systems | Daily-return loop measurable |
| 11 | Blockers, tier 2+: cannons, portals, gates, conveyors, spreading hazards, timed bombs, multi-stage blockers | Level variety stops being the bottleneck |

Phases 1–3 are the game. Phases 8–10 are the business. Phase 11 is the content
treadmill. Do not reorder.

---

## 10. Platform notes

- The shipped game is native C++ on an in-house engine with Lua scripting and an
  EnTT ECS, arm64-only, minSdk 24. For a new title, **Unity, Godot, or
  TypeScript/WebGL are all fine** — the core must be engine-agnostic and headless,
  which matters far more than the engine choice.
- Keep the APK/IPA small and stream content: the shipped base APK is 93 MB with a
  44 MB level database, and everything beyond level ~7,500 of map data is
  downloaded.
- 64-bit only is now safe; design for Privacy Sandbox topics/attribution rather
  than the ad ID.
- Billing, consent (GDPR/ATT) and an account/cloud-save path are prerequisites for
  monetisation, not follow-ups.
