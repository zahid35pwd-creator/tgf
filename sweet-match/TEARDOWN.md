# Candy Crush Saga 1.340.0.1 — APK Teardown

Source artifact: `Candy+Crush+Saga_1.340.0.1_APKPure.xapk` (179,528,048 bytes)
Analysed by static unpacking, level-corpus statistics, and native-binary string/RTTI mining.
No code from the package was executed.

Every claim is marked **[observed]** (read directly out of the package),
**[derived]** (statistically proven across the 23,885-level corpus), or
**[inferred]** (strong circumstantial evidence, flagged as such).

---

## 1. Package layout

XAPK = split-APK bundle, 22 entries. **[observed]**

| Split | Size | Contents |
|---|---|---|
| `com.king.candycrushsaga.apk` (base) | 93.2 MB | 5 dex files, `resources.arsc`, **10,097 files**, all game assets |
| `config.arm64_v8a.apk` | 84.9 MB | `libcandycrushsaga.so` (83.9 MB), `libapplovin-native-crash-reporter.so`, `libdatastore_shared_counter.so` |
| `config.xxhdpi.apk` | 240 KB | density-specific Android drawables |
| `config.<lang>.apk` × 18 | 37–70 KB | Android string resources per locale |

Manifest facts **[observed]**:

```
package        com.king.candycrushsaga
versionName    1.340.0.1          versionCode 13400010
minSdk 24 (Android 7.0)           targetSdk 37
main activity  com.king.candycrushsaga.CandyCrushSagaActivity
backend host   ccs.play.king.com
```

21 permissions. Notable: `com.android.vending.BILLING`, `POST_NOTIFICATIONS`,
`WAKE_LOCK`, `FOREGROUND_SERVICE`, `AD_ID`, plus the whole Android Privacy
Sandbox set (`ACCESS_ADSERVICES_AD_ID / ATTRIBUTION / TOPICS / CUSTOM_AUDIENCE`).
No storage, camera, location or contacts permissions.

Only **arm64-v8a** ships — no 32-bit split, so a hard floor of 64-bit devices.

---

## 2. Technology stack

### 2.1 Native C++, not Unity / Unreal / Cocos

`libcandycrushsaga.so`: `ELF 64-bit LSB shared object, ARM aarch64, for Android 24,
built by NDK r27c (12479018), stripped`, 83,963,536 bytes. **[observed]**

The engine is King's in-house **"FictionFactory" (FF)**. Evidence **[observed]**:

- `packages.xml` root node is literally `FictionFactory`.
- Asset extensions `.ffm` (mesh/model), `.ffa` (animation clip), `.ffsb` (compiled shader).
- Bundled packages named `ff-builtin-assets`, `ff-optional-assets`.
- Shader names prefixed `FF-` (e.g. `FF-FontAlphaTextureColor`).
- Engine settings at `assets/res_output/configs/common_live.settings`.

Engine configuration, verbatim **[observed]** (abridged):

```json
"UseGameComponentSystem": true,          // ECS
"Scripting.EnableScriptEngine": true,    // Lua
"MaxNumberOfParticleEmitters": 512,
"MaxBatchedVerticesPerMesh": 512,
"MaxTotalBatchedVertices": 4096,
"Audio.MaximumInMemoryMusicFiles": 2,
"BitmapFontUsingSdfShaderName": "CCSFontsBitmapSDF",   // SDF text
"UiCameraName": "Ortho",  "UiDepthSorting": "FromCamera",
"AppNotRespondingDetectorEnabledOnStart": true,
"AppNotRespondingDetectorTimeThresholdMs": 10000
```

`entt::internal::pretty_function()` symbols show the ECS is built on **EnTT**
**[observed]**. Mangled RTTI shows heavy `std::unique_ptr`, `std::function`,
`king::string_view`, `king::expected`, `king::cancel_token` — a modern C++17/20
codebase with its own std-adjacent utility library.

### 2.2 Shared-engine fingerprints

The binary contains popup and scene identifiers for **other King titles** compiled
into the same framework **[observed]**: `BWS3*` (Bubble Witch 3), `Stritz*`,
`Rakdos*`, `Frodo*`, `Farmking*`, `PSS*`, `CK*`, `BW2*`, `Dachs`. This is a
multi-game platform, not a one-off codebase.

### 2.3 Java/Kotlin layer = shell + SDK wall

5 dex files (~37 MB uncompressed). Package frequency from dex string mining **[observed]**:

| Area | SDKs present |
|---|---|
| Ads / mediation | AppLovin MAX, IronSource/LevelPlay, Unity Ads, Vungle, Moloco, Meta Audience Network, Mintegral/MBridge, Smaato, Chartboost, HyprMX, Fyber Inneractive, Bytedance/Pangle, PubNative, Amazon device IAP, Google AdMob, IAB OMID viewability |
| Analytics / attribution | Firebase Analytics, AppsFlyer, Braze (push, triggers, content cards), King's own `com.king.usdk.*` and `com.king.abm` |
| Billing | Google Play Billing (`ProxyBillingActivityV2`), Amazon IAP |
| Consent / privacy | OneTrust (`com.onetrust.otpublishers`), IronSource AdQuality |
| Platform | AndroidX (Compose, Media3/ExoPlayer, WorkManager, DataStore, Room/SQLite), Kotlin coroutines + serialization, **Ktor** HTTP client, Glide, Protobuf, ZXing |
| King internal | `com.king.usdk` (kdid, cloudstorage/backup agent, localnotification, nativesharing, otconsent, braze bridge, Ada support chat), `com.king.core`, `com.king.store`, `com.king.storemodule`, `com.king.web`, `com.king.google` |

### 2.4 Asset pipeline formats

| Extension | Count | Meaning |
|---|---|---|
| `.xml` | 4,378 | Scenes, atlases, particles, timelines, materials — stored as **`BIN1`**, King's binary-serialised XML (magic `BIN1` + root name + null-terminated string stream) |
| `.json` | 1,106 | Configs, event definitions, Spine skeletons, data sources |
| `.webp` | 641 | Texture atlas pages, each with a sibling `.xml` atlas descriptor |
| `.ktp` | — | "King Texture Package", the name the runtime requests; shipped file is the `.webp`/`.png` with the same stem |
| `.ogg` | 307 | All audio (Vorbis) |
| `.prefab` | 263 | Scene prefabs |
| `.ng` | 183 | Node graphs (visual logic) |
| `.ffm` | 173 | Meshes/models |
| `.ffsb` | 156 | Compiled shaders |
| `.csv` | 141 | Localisation tables |
| `.ffa` | 134 | Animation clips |
| `.preload` / `.index` / `.manifest` | 79 / 79 / 70 | Per-package preload groups and content indexes |
| `.skel` / `.spine` / Spine `.json` | 10 | Spine 2D skeletal animation |
| `.lua` | 11 | Gameplay/HUD binding scripts |

Content is split into **79 addressable packages** under
`assets/res_output/bundled/`, each with `package.index`, `package.manifest`,
`.md5` sidecars and `preload_groups/*.preload`. Textures ship in two tiers:
`assets/` (SD, 59 MB) and `assets_hd/` (HD, 7.9 MB) **[observed]** — HD overrides
only the subset that benefits.

`ota-*` packages (`ota-curved_map-autumn`, `ota-dynamic_difficulty_v6-default`,
`ota-dynamic_difficulty_ps-default`, `ota-first_attempt_challenge-default`) carry
`ota_metadata.json` with `compatibleMajorVersions` and semver crates — a live
content-delivery system that replaces these folders without an app update **[observed]**.

---

## 3. Content scale

| Thing | Count |
|---|---|
| Levels bundled in the APK | **23,885** |
| Episodes | **1,593** (15 levels each; the first two have 10) |
| Map "worlds" with bundled layout | 507 (cover levels 1 → 7,520; the rest streams via OTA) |
| Distinct board element codes in use | **185** |
| Localisation languages | 26 (plus separate `countries`, `gifting`, `legaltext` tables) |
| Named UI popups referenced in the binary | ~1,000+ |
| SFX | 203 in-game + 74 menu/map |
| Voice-over clips | 18 |
| Music tracks bundled | 4 (one gameplay, one menu/map, win + lose stingers); the rest streams |
| Fonts | 10 TTF + 3 OTF + 25 compiled `.font` descriptors |

Fonts shipped **[observed]**: `king_super_grotesk_offc_pro{,_medium}.ttf`,
`king_super_happy_grotesk_ruika.otf`, `DroidSans.HappyRuika.ttf`,
`BubblegumBliss.otf`, `sky_gothic_150.otf`, `roboto_flex_variable.ttf`, and
Noto Sans / Noto Sans Thai / Noto Kufi Arabic for script coverage.

---

## 4. The level format, fully decoded

### 4.1 Storage

```
assets/res_output/bundled/ccsm_levels/assets/
  levels.xml                        2.6 MB  BIN1 index: filename, hll, qa,
                                            levelDefinitionId_meta, levelVersion
  levels_batched/levels_batched.db   44 MB  SQLite 3
  levels/                                   5 loose .txt overrides (tutorials)
```

SQLite schema, verbatim **[observed]**:

```sql
CREATE TABLE batched_levels (
    ordinal    INTEGER PRIMARY KEY,
    level_data BLOB NOT NULL          -- plain UTF-8 JSON, NOT compressed
);
CREATE TABLE level_metadata (
    ordinal             INTEGER PRIMARY KEY,
    hll                 INTEGER NOT NULL,   -- "hard level" tier
    level_definition_id INTEGER NOT NULL,
    level_version       INTEGER NOT NULL
);
```

23,885 rows in each. `ordinal` ↔ `levels.xml` entry order ↔ `id_meta` in the JSON.

### 4.2 Level JSON

Protocol version `"0.3"`. A real level (ordinal 1 = level 2) **[observed]**:

```json
{
  "protocolVersion": "0.3",
  "numberOfColours": 4,
  "preferredColors": [0,1,2,3],
  "boardRows": 9, "boardColumns": 9,
  "randomSeed": 0,
  "qa": false,
  "disablePreLevelBoosters": false,
  "scoreTargets": [3500, 41918, 84710],
  "moveLimit": 25,
  "gameModeName": "Order",
  "levelType": [],
  "tileMap": [ ["000","000","000","000","000","000",
                "001005026002058","001005026002059","001005026108002058"], ... ],
  "stripedRowCandySpawn": 2,
  "stripedColumnCandySpawn": 2,
  "_itemsToOrder": [ { "item": 17, "quantity": 25 } ],
  "levelDefinitionId_meta": 104104066, "version_meta": 14, "id_meta": 2
}
```

Every key in the corpus, with occurrence counts **[observed]**:

```
23885  protocolVersion numberOfColours randomSeed scoreTargets moveLimit
       gameModeName tileMap levelDefinitionId_meta version_meta id_meta
23616  qa                        23253  levelType
14394  boardRows boardColumns    13944  episodeId          13936  levelId
13005  preferredColors           11565  portals            11447  _itemsToOrder
 9982  stripedCandySpawn          9945  disablePreLevelBoosters
 8699  ingredients                7979  cannons / evilSpawnerCount
 7926  gates                      7400  stripedColumnCandySpawn
 7259  stripedRowCandySpawn       7091  wrappedCandySpawn
 7029  evilSpawnerElements / evilSpawnerAmount
 5917  ammoCannons                5859  orlocks            5675  fishSpawn
 5673  ingredientSpawnDensity     5508  evilSpawnerInterval
 5432  mulockCandySpawn           4853  licoriceMax        4803  pepperCandyExplosionTurns
 4655  licoriceSpawn              4553  tileGroups         4260  mulockCandyMax
 4189  fallingIcingSpawn          4175  fallingIcingMax    4093  colorBombSpawn
 3992  luckyCandyMax              3943  luckyCandySpawn    3921  stripedCandyMax
 3875  wrappedCandyMax            3836  shieldLevel        3793  mysteryCandyMax/Spawn
 3793  chameleonCandyMax/Spawn    3791  stripedRowCandyMax
 3766  stripedColumnCandyMax      3764  isOwlModeEnabled   3757  shieldSpawn
 3600  pepperCandyMax/Spawn       3581  shieldMax          3457  fishMax
 3417  colorBombMax               3385  skulls             3057  fallingIcingLevel
 2882  maxNumIngredientsOnScreen  2650  rainbowRapidsTargets
 2453  numIngredientsOnScreen     1334  extraMovesMin      1327  frogStomachSize
 1216  extraMovesSpawn            1076  wonderfulWrappers   870  candyCobras
  269  leftWeightToTriggerMoonStruck / rightWeight… / totalWeight… /
       useSplitWeightConditionToTriggerMoonStruck / maxAllowedScaleDiff /
       useTotalWeightConditionToTriggerMoonStruck / initialMovesUntilMoonStruck /
       initialMovesDuringMoonStruck
  222  timeCandyMax/Spawn          213  objectives          162  name
  118  extraMovesSpawnRate          21  butterscotchBashers
    7  custom_score_match            2  randomConfig          1  candyBars
```

Board geometry **[observed]**: **9 × 9 always**. 14,384 levels state `boardRows: 9`,
10 outliers state 11, and the 9,491 levels that omit the key still serialise 9 rows
of 9. `moveLimit` ranges 14–37, mode 15 (4,889 levels). `numberOfColours`:
5 (12,760), 4 (10,171), 3 (870), 6 (48), 2 (36).

### 4.3 `tileMap` encoding — how one cell works

Each `tileMap[row][col]` is a **string of concatenated 3-digit element codes**,
read left to right as a bottom-up layer stack. `"000"` alone means no tile.

```
"001005026002058"  →  001 tile
                      005 spawner
                      026 spawner emits normal candy
                      002 candy occupant
                      058 colour tag 3
```

The same 3-digit enum is reused for `cannons` ammunition lists —
`[[26],[26],[26,107]]` loads a normal candy, a normal candy, then a striped-row
candy — which proves these are global element IDs, not per-field codes **[observed]**.

### 4.4 Element codes — verified map

Verified by two-way set equivalence against level features across all 23,885
levels; both `P(feature|code)` and `P(code|feature)` are reported. **[derived]**

| Code | Meaning | Evidence |
|---|---|---|
| `000` | No tile / hole | 275,132 of 275,138 occurrences are the only code in the cell |
| `001` | Playable tile (ground) | 711,420 / 725,687 at slot 0; ~50 per level = a full 9×9 playfield |
| `002` | Candy occupant | present in 23,839 / 23,885 levels; 99,167 cells are `002` alone = random colour |
| `003` | **Double jelly** | occurs only in Jelly modes; a strict subset of `004` levels |
| `004` | **Jelly (single)** | in 93 % of *Light up*, *Jelly Order*, *Jelly Drop down*, *Rainbow Rapids Jelly*; absent from *Order*, *Drop down*, *Order Drop Down*, *Rainbow Rapids*, *Rainbow Rapids Order* |
| `005` | Candy spawner / generator | in **all** 23,885 levels, ≈9.9 per level = one per column, always the topmost tile of its column |
| `010` | Ingredient (cherry / nut) | ⟺ `ingredients`, Jaccard **1.000** |
| `011` | Portal tile | ⟺ `portals`, Jaccard **1.000** |
| `012` | Portal exit marker | ⟺ `portals` 1.000; never slot 0; always follows `011` |
| `013` | Portal entry marker | ⟺ `portals` 1.000; never slot 0; always follows `011` |
| `026` | Spawn-table entry: normal candy | 190,795 of 222,921 directly follow `005`; also cannon ammo id 26 (73,123 loads) |
| `027` | Spawn-table entry: ingredient density | P(`maxNumIngredientsOnScreen`) = 0.78, P(`ingredientSpawnDensity`) = 0.66 |
| `028` | Spawn-table entry: licorice swirl | P(`licoriceMax`\|code) = 0.83, reverse **1.00** |
| `030` | Spawn-table entry: sugar-lock candy | P(`mulockCandySpawn`\|code) = 0.88, reverse **1.00** |
| `055`–`060` | **Colour tags 0–5** | exactly six consecutive codes; 15,315 of 32,666 `055` directly follow `002`; they can chain (`055`+`056`+…) to express multi-colour requirements |
| `062` | Chocolate / "evil" spawner | P(`evilSpawnerElements`\|code) = **1.00**; equals order item 24 |
| `068` | Spawn-table entry: lucky candy | P(`luckyCandyMax`\|code) = 0.90, reverse 0.91 |
| `071` | Spawn-table entry: striped (either axis) | ⟺ `stripedCandySpawn`, Jaccard 0.986 |
| `072` | Spawn-table entry: wrapped | ⟺ `wrappedCandySpawn`, Jaccard 0.975 |
| `090` | Spawn-table entry: falling icing | P(`fallingIcingLevel`\|code) = 0.76, reverse **1.00** |
| `107` | Spawn-table entry: striped-row | ⟺ `stripedRowCandySpawn`, Jaccard 0.980 |
| `108` | Spawn-table entry: striped-column | ⟺ `stripedColumnCandySpawn`, Jaccard 0.979 |
| `125` | Spawn-table entry: ingredient | P(`ingredients`\|code) = **1.00**, Jaccard 0.886 |
| `127` | Spawn-table entry: colour bomb | ⟺ `colorBombSpawn`, Jaccard 0.924 |
| `128` | Spawn-table entry: jelly fish | ⟺ `fishSpawn`, Jaccard 0.978 |
| `137` | Spawn-table entry: shield | P(`shieldLevel`\|code) = 0.99, reverse **1.00** |
| `140`, `141`, `142`–`145`, `152`–`156` | Rainbow Rapids tiles / bridges / targets | `156` ⟺ `rainbowRapidsTargets` Jaccard **1.000**; the others P(feature\|code) = 1.00 |
| `180` | Skull / pedestal | ⟺ `skulls`, Jaccard 0.999; equals order item 34 |
| `181` | Orlock (order-lock blocker) | ⟺ `orlocks`, Jaccard **1.000** |
| `214`–`219` | Spawner colour restriction 0–5 | exactly six consecutive; 1,127 `214` directly follow `005`; also cannon ammo ids 214–219 |
| `227` | Candy Cobra / snake | ⟺ `candyCobras`, Jaccard **1.000**; equals order item 37 |
| `228` | Wonderful Wrapper | ⟺ `wonderfulWrappers`, Jaccard **1.000**; equals order item 38 |
| `232` | Dragon Nest | named in `tutorials.xml` as blocker id 232 |
| `233` | Heavy Layered (Citrus Chews) | `tutorials.xml` blocker id 233; equals order item 41 |
| `234` | Colour Cluster | `tutorials.xml` blocker id 234; equals order item 42 |
| `230`, `231`, `235`, `236` | Order-target blockers | equal order items 39, 40, 43 and its pair |
| `006` | Moonstruck scale | P(`…ToTriggerMoonStruck`\|code) = 0.97 |
| `009` | Order item 16's board element | P(code \| orders item 16) = 1.00, lift 88× |
| `035` | Gummy Ropes | P(code \| orders item 22) = 1.00, lift 16.5×; `tutorials.xml` names blocker 35 "gummy ropes" |

**Multi-layer blockers occupy consecutive code runs**, one code per layer count.
Clear runs in the corpus **[inferred, from run structure plus the sprite atlas]**:

```
019-023  5 layers    079-083  5 layers    085-089  4-5 layers
109-112  113-116  117-120   4 layers each
129-133  5 layers    159-163  5 layers
164-167  168-171  172-175  176-179   4 layers each
122-124  134-136   3 layers each      038-042  paired
182-201  several short runs
```

Matching sprite sets in `tex/candy.xml` are `block_multi_frosting1..5`,
`block_waffle1..5`, `chain_layer1..5`, `mulock1..5`, `chocolate_spawn_1..5`,
`destructible_wall_1..3`, `glass_tile_1..3`, `chocolate2_1..3`, `sugarcoat_1..3`,
`bubble_pop_0..5`, `gumball_1..5`, `bonbon_blitz_1..4`, `state_01..03`.
`tutorials.xml` pins **waffle = board_item id 19**, making `019`–`023` the
strongest candidate for waffle 1–5 **[inferred]**. The remaining runs cannot be
assigned numerically without decompiling the parser, so they are left unasserted.

### 4.5 Structured (non-`tileMap`) level fields

All shapes verbatim from the corpus **[observed]**:

```jsonc
"gates":   [ [[4,8],[4,7],[0],[1]], ... ]   // [fromRC, toRC, direction, type]
"portals": [ [[0,4,14],[0,6,14]], ... ]     // [[entryR,entryC,14],[exitR,exitC,14]]
"skulls":  [ [1,4,1,true,false], ... ]      // [r,c,level,isHead,isTail]
"orlocks": [ { "data":[4,6,1,1,17,20], "type":0, "skin":0 } ]
                                            // [r,c,w,h,orderItemId,quantity]
"tileGroups": [ { "type":"gummyRope",
                  "tiles":[[8,8],[8,7],[8,6]], "params":{} } ]
"cannons":     [ [26], [26], [26,107] ]     // per-cannon ammo queue
"ammoCannons": [ [26], [26] ]
"candyCobras": [ [4,5,4,4,8], ... ]         // [r,c,len,tailR,tailC]
"wonderfulWrappers": [ [4,2,1,0,1,4,-1,-1,44,44,3,3], ... ]
"candyBars": [ [0,7,null,null,null,null,null,null,null,null,null,null] ]
"ingredients": [0, N]                       // [type=0, count 1..60]
"rainbowRapidsTargets": [5,10,9,2,4]
"custom_score_match": [6000, 538]
"randomConfig": { "mode":"Numbers Pool", "modeModifier":"Default", "seedPool":[2] }
"objectives": [ {"type":"ORDER","item":17,"amount":51},
                {"type":"INGREDIENTS","amount":3} ]   // newer "Flexible" mode
```

`_itemsToOrder` uses a **separate** order-item enum, 1…43. Confirmed anchors
**[derived]** from the in-binary debug help plus code correlation:

```
 1 Striped Row        2 Striped Column    3 Wrapped        5 Colour Bomb
 6 Swedish/Jelly Fish 12 Coconut Wheel    13 Joker/Lucky   18 UFO
19 Waffle (tutorials.xml)                 22 Gummy Ropes   24 Chocolate spawner
33 Jelly Jar (tutorials.xml)              34 Skull         37 Candy Cobra
38 Wonderful Wrapper  41 Heavy Layered / Citrus Chews      42 Colour Cluster
17, 25, 29, 32 — the four highest-volume order targets (4,810 / 4,750 /
1,630 / 1,510 levels), uncorrelated with any board element, i.e. ordinary
candy classes
```

---

## 5. Game modes

`gameModeName` values and counts over the bundled corpus **[observed]**,
cross-checked against the `CGameMode*` RTTI classes in the binary:

| `gameModeName` | Levels | Engine class | What it is |
|---|---|---|---|
| `Light up` | 4,885 | `CGameModeJelly` | Clear all jelly, "lighting up" every tile |
| `Order` | 4,196 | `CGameModeOrder` | Collect N of the listed items |
| `Jelly Order` | 3,759 | `CGameModeJellyOrder` | Jelly **and** order |
| `Jelly Drop down` | 3,298 | `CGameModeJellyIngredients` | Jelly **and** ingredients |
| `Order Drop Down` | 2,688 | `CGameModeOrderIngredients` | Order **and** ingredients |
| `Drop down` | 2,409 | `CGameModeIngredients` | Bring N ingredients to the bottom exits |
| `Rainbow Rapids Jelly` | 814 | `CGameModeRainbowRapidsJelly` | Rapids + jelly |
| `Rainbow Rapids Order` | 804 | `CGameModeRainbowRapidsOrder` | Rapids + order |
| `Rainbow Rapids` | 728 | `CGameModeRainbowRapids` | Route candy through a bridge graph |
| `Rainbow Rapids Drop Down` | 304 | `CGameModeRainbowRapidsIngredients` | Rapids + ingredients |
| — | — | `CGameModeClassic` | Score-only (legacy / event levels) |
| — | — | `CGameModeFlexible` | Data-driven objectives (the `objectives` array, 213 levels) |

`levelType` is a tag array for analytics and quest targeting, not rules
**[observed]**: `Excavation` (11,385), `Explosive` (9,614), `Sniping` (4,114),
`Endurance` (3,588), `LimitedPlaySpace` (1,783), `Puzzle` (1,331).

---

## 6. Gameplay rules recovered from shipped config

### 6.1 Scoring — `candycrushsaga/assets/scoreconfig.prop` **[observed, verbatim]**

```
jellyScore                    1000     match4BaseScore            20
itemBaseScore                   20     match5BaseScore            20
blockerScore                    20     sugarCrushFish           2000
sugarCrushMoveToSpecialScore  6000     removedByPowerUpMultiplier  1
ingredientCollectedScore     10000     orderGrade1               100
pepperScore                    500     orderGrade2              1000
cakeBombCandyScore             100     orderGrade3              5000
rainbowRapidsCandyScore      10000     orderGrade4             10000
fishRemovalScore               250     stripedRemovalScore       500
colorBombRemovalScore         5000     wrappedRemovalScore      1000
```

The three `scoreTargets` per level are the 1★ / 2★ / 3★ thresholds.

### 6.2 Tuning — `tweakdata.prop` **[observed, verbatim highlights]**

```
# board physics and timing (ms unless noted)
board.slide.speed 5.5                board.speed.factor 0.032
board.max.speed 0.4                  board.tickstodestroy.default 4
board.item.remove.time 300           board.fadetime 800
shuffle.time 1300                    shuffle.time.delay 800
gameboard.screenpadding.percent 0.035

# lives economy
lives.base.max 5                     lives.gain.interval 1800     # 30 min per life
lives.gift.interval.hours 18         send.extra.life.interval.hours 24
send.extra.moves.interval.hours 24   charm.life.extra.lives 3

# hints and idle nudges
highlight.move.interval 3000         highlight.move.time 2300
shuffle.booster.recommendations.idletime.seconds 20
shuffle.booster.recommendations.specialboard.firstturns 5
shuffle.booster.recommendations.specialboard.maxpossiblemoves 1
shuffle.booster.recommendations.pepperbomb.ticks.remaining 3
shuffle.booster.recommendations.blockersorspecials.maxturns 5

# end of level
level.complete.pause 800             level.complete.sugar.crush.delay 500
level.complete.sugar.crush.pause 800 level.complete.sugar.crush.skip.delay 10.0
supersugarcrush.screenshake.delay 3000

# special-candy choreography
matcher.wrapline.tickstodestroy 58   matcher.wrapwrap.tickstolive 18
destructionplan.line.speed 0.7
destructionplan.line.3x3.delay.horizontal 10
destructionplan.line.3x3.delay.vertical 28
destructionplan.colorbomb.removedelay 3
destructionplan.colorbomb.totalticksbase 10
cake.bomb.prepare.delay 500          cake.bomb.candy.removal.start.delay 1200
stripedwrapped.screenshake.delay.first 430 / .second 530
party.booster.candy.removal.start.delay 3000
party.booster.pinata.explode.ticks 450
lollipop.booster.hammer.delay 880    paintbrush.booster.delay 700
pepperbomb.gum.clear.time 1600       board.animationinterval.pepperbombfuse 400
coconut.wheel.speed 0.0032           ufo.logic.lift_off_duration 1000
sugarcoat.delay.ms 350               bubblepop.removal.delay.ticks 20

# HUD layout (fractions of screen)
gamehud.top.bar.height.percent.portrait 0.17   (max 240 px)
gamehud.bottom.height.percent.portrait 0.16    (max 200 px)
gamehud.moves.left.warning.threshold 5
gamehud.moves.left.warning.interval 400
infobanner.height.percent.portrait 0.15

# progression and meta
rate.popup.show.after.levelindex 531
mystery.quests.start.level.index 10  mystery.quests.interval 86400
dreamworld.episodes.start 1201       dreamworld.episodes.end 1247
seedpool.initial.range 1000
end.game.bundle.bonus.moves.min/mid/max 5/15/50
end.game.bundle.bonus.time.min/mid/max 15/30/60

# server poll intervals (s)
rpc.request.usermetrics.interval.seconds 36000
rpc.request.abcase.interval.seconds 7200
rpc.request.appsettings.interval.seconds 7200
rpc.request.paystatedata.interval.seconds 3600
```

### 6.3 Special candies and the combination matrix

Special item type IDs, from the in-binary debug console help **[observed, verbatim]**:

```
boardaction morphpregamebooster
    1  (Striped Row)      2  (Striped Column)   3  (Wrapped)
    5  (Color Bomb)       6  (Swedish Fish)    12  (Coconut Wheel)
   13  (Joker)           18  (UFO)
morphpattern <itemtype> <pattern>   patterns: wave, heart, romb,
                                    or a rows*columns '0'/'1' mask
```

Combination pairs exist as discrete effect identifiers **[observed]**:

```
striped_x_striped      striped_x_wrapped     wrapped_x_wrapped
striped_x_colorbomb    wrapped_x_colorbomb   colorbomb_x_colorbomb
fish_x_fish            fish_x_striped        fish_x_colorbomb
bomb_x_striped         pgpstriped_x_wrapped
```

Destruction is planned, not immediate — the engine builds a *destruction plan*
object per effect. Plan classes in RTTI **[observed]**: `CDestructionPlanLine`,
`CDestructionPlanColorBomb`, `CDestructionPlanQuantumColorBomb`,
`CDestructionPlanFish`, `CDestructionPlanIngredient`, `CDestructionPlanFrog`,
`CDestructionPlanCrystalCandy`, `CDestructionPlanSpawnBlocker`.

### 6.4 Difficulty system

Static tiers, from the debug console help **[observed, verbatim]**:

```
hardlevel <level-id> <difficulty-value> | reset
Difficulty values are 0=normal, 1=hard, 2=super hard, 3=crazy hard, 4=legendary hard
```

matched by UI scenes `_normal_` / `_hard_` / `_super_hard_` /
`_nightmarishly_hard_` / `_legendary_hard_level_objective_panel.xml`, and by the
`hll` column in `level_metadata` **[observed]**.

Two live difficulty-modulation systems ship as OTA crates **[observed]**:

- **`dynamic_difficulty_v6`** — `[[levelOrdinal, levelDefinitionId, threshold], …]`
  for thousands of levels, in two A/B cases (`ddv6_threshold_case_1.json`,
  `_case_2.json`), thresholds 5–43. Plus `dynamic_difficulty_ps` with a
  `level_exclusion_list.csv`.
- **`first_attempt_challenge`** — `[{levelOrdinal, levelDefinitionID, moves}]`:
  an explicit reduced move count applied on the **first** attempt at specific
  levels, in two A/B cases. The player's first try is deliberately harder than
  the retry.

### 6.5 Hints, shuffle, no-moves

- Hint pulse after `highlight.move.interval` 3,000 ms, animating 2,300 ms.
- Board shuffle (1,300 ms plus 800 ms delay) when no moves are possible;
  `shuffling_in.ogg` / `shuffling_out.ogg`.
- A recommendation engine offers the Magic Shuffler when the player is idle 20 s,
  or possible moves ≤ 1, or a pepper bomb has ≤ 3 ticks left.
- The binary carries a bot player (`mGamePolicy.IsBotPlay`) and a
  `countspawnedcolors [start|stop]` instrumentation command — levels are balanced
  by automated playthroughs. **[observed]**

### 6.6 Deterministic randomness

`randomSeed` per level, plus `randomConfig` with
`{"mode":"Numbers Pool","seedPool":[…]}`, plus `seedpool.initial.range 1000` and
`tutorialcandies.txt` holding literal hand-authored colour sequences for tutorial
boards **[observed]**. Spawn order is reproducible from a seed — required for
server-side score validation and for level QA.

---

## 7. Feature catalogue

### 7.1 Boosters

From localisation keys **[observed, verbatim names and descriptions]**:

| Booster | Description string |
|---|---|
| Lollipop Hammer | "Smash Candies or blockers in a single hit." |
| Free Switch | "Switch two candies that don't match!" |
| Striped Brush | "Paints pretty stripes on a candy of your choosing" |
| Magic Shuffler | "Reshuffle your board for new possibilities!" |
| Colour Bomb (pre-level) | "Start the level with a Color Bomb!" |
| Striped & Wrapped (pre-level) | "Start the level with a Striped & Wrapped Candy!" |
| Jelly Fish (pre-level) | "Add Jelly Fish candies to the candy mix." |
| Coconut Wheel (pre-level) | "Add Coconut Wheels to the candy mix!" |
| Lucky Candy / Joker | "Adds Lucky Candy to the board." |
| Extra Moves | +5 moves (`show.extra.moves.booster.limit 5`) |
| Party Booster | "Break the Piñata to clear the game board and release 4 special candies that detonate." |
| UFO | "Summon a tasty UFO that zaps the board creating 3 wrapped candies that explode." |
| Moonstruck | "removes at least one candy color for 5 moves" |
| Extra Lives | — |

Boosters have three durability classes in the item enum **[observed]**: plain
(consumable), `SEMIDURABLE_*` (ids 21–32, 64 — timed or limited unlimited use),
`LEVELDURABLE_*` (id 65), plus `END_GAME_*` variants (ids 34–43, 59) sold on the
fail screen.

### 7.2 Full item / currency enum

Extracted from the binary **[observed]**. This *is* the economy:

```
  0 EMPTY/NONE              1 HARD_CURRENCY (gold bars)   2 PIGGY_BANK_HARD_CURRENCY
  3 ONE_LIFE                4 FULL_LIFE                   5 UNLIMITED_LIFE
  6 CHARM_OF_LIFE           8 CHARM_OF_STRIPED_CANDY      9 CHARM_OF_GOLDEN_TICKET_2
 10 COLOR_BOMB             11 FREE_SWITCH                12 LOLLIPOP
 13 COCONUT_WHEEL          14 JOKER_CANDY                15 PAINT_BRUSH
 16 PARTY_BOOSTER          18 STRIPED_WRAPPED            19 SWEDISH_FISH
 20 UFO
 21-32 SEMIDURABLE_{COLOR_BOMB, FREE_SWITCH, LOLLIPOP, COCONUT_WHEEL, JOKER_CANDY,
       PAINT_BRUSH, PARTY_BOOSTER, STRIPED, WRAPPED, STRIPED_WRAPPED,
       SWEDISH_FISH, UFO}
 33 EXTRA_MOVES            34 END_GAME_EXTRA_MOVES       35 END_GAME_EXTRA_TIME
 36 END_GAME_ANTI_PEPPAR   37 END_GAME_COLOR_BOMB        38 END_GAME_SWEDISH_FISH
 39 END_GAME_COCONUT_WHEEL 40 END_GAME_JOKER_CANDY       41 END_GAME_STRIPED
 42 END_GAME_WRAPPED       43 END_GAME_STRIPED_WRAPPED   59 END_GAME_UFO
 44 WHEEL_SPIN_BOOSTER     45 WHEEL_SPIN_EGP             46 WHEEL_SPIN_BOOSTER_TOKEN
 47 MINI_ADS_GAME_HAMMER   48 MINI_ADS_GAME_DUDS         51 ADS_JACKPOT
 53 ON_FIRE_TIER           54 SEASON_PASS                55 SEASON_PASS_TIER
 61 DOUBLE_COLOR_BOMB      62 SEASON_MASTERY_POINT       63 EXPANDED_LIVES_STORAGE
 64 SEMIDURABLE_SUPER_WRAPPED                            65 LEVELDURABLE_LOLLIPOP
 66-70 PIN_PACK_1..5       74-78 PIN_PACK_WITHREPLACEMENT1..5
 71 MINI_GAME_TOKEN        72 MINI_GAME_TOKEN_BATTLECOOKIES
 73 MINI_GAME_TOKEN_CANDYCODES
 79-81 CHEST_BRONZE/SILVER/GOLD                          82 CHOCOLATE_BOX_PIECE
 83 EPISODE_RACE_DOUBLE_REWARD_TOKEN                      85 ADS_EPISODE_RACE_PASS
 86 CITRUS_BEES            88 PRIZE_DRAW_TICKET_TOKEN
 89 PURCHASE_PERK_EXPANDED_LIVES                          90 PURCHASE_PERK_LEFTOVER_MOVES
 92 SOFT_CURRENCY          93 TREAT_MACHINE_STREAK_SAVER  94 SEASON_PASS_REVAMP_PASS
 96 NO_ADS_PASS            97 PURCHASE_PERK              98 MAGIC_PIN
 99 MERGE_MINIGAME_TOKEN  100 MERGE_MINIGAME_PREMIUM_PASS
```

Grades: `BRONZE 0 / SILVER 1 / GOLD 2`. Prominence: `DEFAULT 0 / HIGH 200 / HIGHEST 400`.

### 7.3 Live-ops features, each its own content package

From `assets/res_output/bundled/` directory names and the binary's popup
registry **[observed]**:

**Progression and map** — saga map with curved-path terrain
(`ota-curved_map-autumn`), episodes of 15 levels, episode trophies
(bronze/silver/gold/diamond), Dream World (episodes 1201–1247), golden pins,
master stars, level pins with mods and decorations, `map-refresh`, a
10,000-level milestone badge.

**Competitive** — `episode_race`, `weekly-race`, `marathon_leagues`,
`short_leaderboard_tiers`, `prize_draw`, Scorenament, friends leaderboards.

**Event systems** — `season-pass`, `season-pass-revamp`, Chocolate Box,
`treasure_defense`, `expeditions`, `tap_them_all`, `merge_minigame`,
`friends-challenge-v2`, `onfire` (streaks), `sale`, `bank_seeding` (piggy bank),
`speed_select`, `time_pressure`, `vanity_item_system_plugin`,
`ccsm-quest-system` plus `ccsm-quest-system-common-quests`, mystery quests
(daily, from level 10), `ccsm_chests`.

**Monetisation** — `candy_shop` (bundle configs, endless offers, segmented
offers, milestone offers, dynamic offer popups, direct checkout), gold-bar
deals, piggy bank, hard-level offer, out-of-lives purchase flow, web store,
`PURCHASE_PERK_*`, `NO_ADS_PASS`.

**Ads** — `ads_arcade`, `ads_egp` (extra gameplay), `ads_map_balloon`,
`ads_ool` (out of lives), `ads_prelevel`, `ads_rewards_tiering`, ads quests,
ads jackpot, rewarded mini-games (`MINI_ADS_GAME_HAMMER` / `_DUDS`).

**Social** — Facebook connect, social hub, friend invites and recommenders,
player cards, send-lives/moves gifting, social sharing, Kingdom account
(`kingdomLogin`).

**Platform** — `unified_web_view` (GDPR / RTBF / ToUPP / age-gate / king-account
web views), `monocle` (telemetry), `performance_messaging`, crash compensation,
`cds_binding` (Cloud Data Store), `servicelayer-views`, OneTrust consent, Ada
support chat, haptic feedback, accessibility events.

### 7.4 Gameplay content catalogue, from atlas + SFX + localisation

Candy colours: red, orange, yellow, green, blue, purple — 6 maximum,
`numberOfColours` 2–6 per level **[observed]**.

Specials: striped row/column (`polka_<colour>_horisontal/vertical`), wrapped
(`wonderful_wrapper_*`), colour bomb (`bomb.png`), double colour bomb, super
colour bomb, jelly fish (`fish.skel`), coconut wheel, lucky/joker candy, UFO
(`ufoStill_01`), crystal candy, super wrapped, super line blast, mystery candy,
chameleon candy, pepper candy (timed bomb, `pepperCandyExplosionTurns`), time
candy, extra-moves candy, shield candy, sugar key (`cannon_mulock_key`).

Blockers, from sprite + SFX + tutorial evidence: frosting/icing (multi-layer),
waffle (5 layers), licorice swirl, licorice lock/shell, licorice fence, licorice
curl, marmalade — rebranded **Honey** ("Marmalade is now Honey!"), spreading
chocolate and dark chocolate, chocolate/evil spawner, multi-chocolate, bubblegum
(bubble pop), bonbon blitz, gumball machine, jelly jar, glass tile, sugar coat,
sugar lock / sugar chains (key-matched), mulock, gummy ropes / marshmallow, heavy
layered (citrus chews), colour cluster (colour-specific), destructible walls,
candy cannons and ammo cannons, generators (dormant plus 1–4), candy cobra /
snake, orlock, skull pedestal, dragon nest, taffy puller, shredder, conveyor
belts, gates, portals/teleporters, rainbow twist, wonderful wrapper, butterscotch
bashers, candy bars, moonstruck scale, owl mode (`isOwlModeEnabled`), frog
(`frogStomachSize`), Rainbow Rapids bridges.

Characters in assets: Tiffi (`Tiffi_SpineExport_bigger.json`), Mr. Toffee, Yeti
(`yeti.json`, 903 KB Spine), Odus the owl (`odus_spine.json`), dragon, Bubblegum
Troll, Jane (VO), Crush-o-Master.

Exclamation VO ladder **[observed, verbatim]**: `sweet → tasty → delicious →
divine → marvelous → magnificent → irresistible → unbelievable → frogtastic →
sugar-crush`, plus `super_sugar_crush` and `sugar_stars`.

---

## 8. Engineering practice visible in the strings

- **Debug console shipped in the retail binary**, gated off by `"UseConsole": false`:
  `boardaction hammer/drag/freeswitch/paintbrush`, `morphpattern`, `ufo`, `party`,
  `frogjump`, `booster <type> <n>`, `closefail [x] [i|j|o|r]` (auto-complete a
  level to 95 % score with one objective left), `hardlevel`, `countspawnedcolors`,
  `shuffle`, `get_level_definition_id`, `toggle_visibility`, `translate`,
  `showstringid`. A clone benefits enormously from the same thing.
- **Breadcrumbs plus ANR detector** in the engine
  (`UsingBuiltInBreadcrumbsSystem`, 100-entry ring, 512-byte entries).
- **Popup queue with slots, priorities, whitelists/blacklists and caps**
  (`[PopupQueue] Unknown slot`, `slot cap; use -1 for uncapped`) — the only sane
  way to run 60+ competing live-ops surfaces.
- **A/B testing and holdouts in-engine**:
  `OfflineAbTesting::CManager::ConfigureHoldout`, `rpc.request.abcase.interval.seconds`.
- **Data-broker / data-source / event-definition JSON** decouples UI from logic:
  `events.json` per feature, `data_sources/*.json`, `.ng` node graphs.
- **Lua bindings** expose a stable surface to scripts. `ILevelEndData` gives
  scripts `is_success`, `is_first_success`, `get_score`, `get_grade`,
  `get_previous_grade`, `get_level_attempts`, `get_num_moves`, `get_max_moves`,
  `get_game_round_id`, `get_level_json_hash`, `get_failed_attempt_grade`.
- **Device-resolution test matrix shipped as data**
  (`configs/device_resolutions.json`: Galaxy S9 / S22 / A54 / Z Flip 6 / Z Fold 6,
  iPhone 13, …) plus `layout_configurations.json`.

---

## 9. Legal boundary

The mechanics, data formats, architecture and tuning values documented above are
facts about how the game works, and reimplementing equivalent systems is normal
practice. The package's **art, audio, fonts, Spine rigs, level corpus, character
designs, and the Candy Crush name and branding are King's copyrighted and
trademarked property** — none of it can be shipped or redistributed in a new
game. `CLONE_SPEC.md` is therefore written for an original game: your own art
direction, your own audio, your own authored levels.

---

## 10. Reproducing this analysis

```bash
unzip Candy+Crush+Saga_1.340.0.1_APKPure.xapk -d xapk/
unzip xapk/com.king.candycrushsaga.apk        -d base/
unzip xapk/config.arm64_v8a.apk               -d libs/
```

```bash
python tools/decode_levels.py base/assets/res_output/bundled/ccsm_levels/assets/levels_batched/levels_batched.db --report
```
