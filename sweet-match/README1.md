# Candy Crush Saga 1.340.0.1 — reverse-engineering notes and clone spec

Analysis of `Candy+Crush+Saga_1.340.0.1_APKPure.xapk` (179.5 MB, package
`com.king.candycrushsaga`, versionCode 13400010).

| File | What it is |
|---|---|
| [TEARDOWN.md](TEARDOWN.md) | What is actually in the APK: engine, native library, SDK stack, asset pipeline, the fully decoded level format with a verified element-code table, game modes, scoring and tuning constants, difficulty systems, booster and economy enums, the live-ops feature catalogue |
| [CLONE_SPEC.md](CLONE_SPEC.md) | How to build an original game with the same class of systems: architecture, core match-3 algorithms, cell/blocker model, objective system, level schema and content pipeline, retention and monetisation layers, tooling, phased build order |
| [tools/decode_levels.py](tools/decode_levels.py) | Read-only reader and analyser for the bundled 23,885-level SQLite corpus |

## Headline findings

- **Native C++**, not Unity or Unreal. One 83.9 MB stripped arm64 `.so` built with
  NDK r27c, on King's in-house **FictionFactory** engine (EnTT ECS, Lua scripting,
  `.ffm`/`.ffa`/`.ffsb` assets, `BIN1` binary XML). The Java layer is an Activity
  host plus ~25 third-party SDKs.
- **23,885 levels** ship inside the APK as **uncompressed JSON in a 44 MB SQLite
  database**, in 1,593 episodes of 15. Board is **9 × 9 in every level**.
- `tileMap` cells are strings of concatenated **3-digit element codes** forming a
  bottom-up layer stack: `"001005026002058"` = tile + spawner + spawns-normal-candy
  + candy + colour-tag-3. 185 distinct codes are in use; 45 are verified by two-way
  set equivalence over the whole corpus, several at Jaccard 1.000.
- **10 game modes** reduce to 4 composable objective primitives (jelly, order,
  ingredients, score) plus Rainbow Rapids.
- **All gameplay tuning is in two plain text files** — `scoreconfig.prop` (21 score
  constants) and `tweakdata.prop` (~150 timing, economy and layout constants).
- **Difficulty is modulated live** by three data-driven systems delivered OTA, each
  A/B-cased: static tiers (normal → legendary hard), dynamic difficulty keyed on
  fail count per level, and a *first-attempt challenge* that cuts the move budget
  on the player's first try at specific levels.
- A full **debug console ships in the retail binary**, disabled by config.

## Running the level tool

```bash
python tools/decode_levels.py path/to/levels_batched.db --report
```

```bash
python tools/decode_levels.py path/to/levels_batched.db --show 1 2 30
```

```bash
python tools/decode_levels.py path/to/levels_batched.db --codes
```

```bash
python tools/decode_levels.py path/to/levels_batched.db --correlate --min-jaccard 0.9
```

`--correlate` re-derives the element-code meanings from scratch, so the table in
`TEARDOWN.md` section 4.4 is reproducible rather than asserted.

## Unpacking the APK yourself

```bash
unzip Candy+Crush+Saga_1.340.0.1_APKPure.xapk -d xapk/
```

```bash
unzip xapk/com.king.candycrushsaga.apk -d base/
```

```bash
unzip xapk/config.arm64_v8a.apk -d libs/
```

The level database is then at
`base/assets/res_output/bundled/ccsm_levels/assets/levels_batched/levels_batched.db`.

## Scope

The documented mechanics, data formats, architecture and tuning values are facts
about how the game works, and reimplementing equivalent systems is ordinary
practice. The package's art, audio, fonts, Spine rigs, level corpus, character
designs and the Candy Crush name and branding are King's copyrighted and
trademarked property and cannot ship in a new game. `CLONE_SPEC.md` is written for
an original title with its own art, audio and authored levels.
