# OMR Scanner — 12-item backlog, done

Branch: `claude/student-id-card-design-sra3e6`
APK: https://github.com/zahid35pwd-creator/tgf/releases/download/omr-scanner-latest/OMR-Scanner.apk

| # | Ask | How it was done |
|---|---|---|
| 1 | Read Roll/Reg/ID/Set/Code/Name/Class **vertically** (column-wise); digits 0–9, letters A–Z | Engine already reads column-wise via `FieldReading`. Fix was in `TemplateLearner.classify()`: give `tallest == 10 && rows.size == 10` priority so 4–6-digit NUMBER fields aren't mislabelled as ANSWERS. Added `classifiesEveryKindOfField` test exercising NUMBER (2..10 digits), NAME (6/10/4 wide × 26), LETTERS (horizontal + vertical), ANSWERS. |
| 2 | After scanning a new OMR sheet, make the user **confirm all rows/columns are read**, else retake | "Check the sheet" step keeps the colour-coded group overlay + numbers + stat strip. Renamed footer button to **"Something's missing"** and added a one-line banner telling the user to confirm every question row and ID column is coloured before saving. |
| 3 | Changing a block type must **not** jump the page to the top | `SheetSetupActivity.showReview()` now saves `scrollY` before rebuild and restores it with `view.post { ... scrollTo(0, savedY) }`. |
| 4 | **Download blank** must offer the three predefined OMR sheets | `HomeTab` tools section now lists one row per `SheetTemplate.ALL` where `printable` — 100q×4, 100q×5, 50q×4 — each calling `SheetPrinter.sharePdf(this, spec)`. BCS/SSC excluded (official source only). |
| 5 | Chip / segmented taps must **not** jump the page to the top | `MainActivity.show()` detects same-tab rebuilds, grabs the current ScrollView's `scrollY` and restores it on the new view. New `findScroll(View)` helper walks the subtree. |
| 6 | **Thumbnail** on predefined OMR cards | `TemplateThumbnail.get(template, widthPx)` draws straight from the template's bubble positions (markers + answer dots + field bubbles), cached per `(id, width)`. `HomeTab.templateCard()` now shows that bitmap in place of the icon badge. |
| 7 | **Thumbnail** on user OMR cards | Same `TemplateThumbnail` works for custom templates (positions come from `LearnedSheet` → `SheetTemplate` on save). No separate bitmap persistence needed. |
| 8 | **Remove print** option | Dropped the print row from `HomeTab`. `SheetPrinter` kept only `sharePdf()` + `AndroidSheetCanvas` — `PrintDocumentAdapter`-based `print()` gone. |
| 9 | **"Edit" label** beside the icon on the active exam card | `HomeTab.examCard()` builds an `editChip` (iconBadge + "Edit" text) in the corner, whole card still tappable. |
| 10 | Set field for **user OMR** on the Key tab | `KeyTab.setChips()` now, when the template is custom and has no `setField`, shows "＋ Add a Set field …" link → bottom sheet listing fields with 2–6 labels (ক খ গ ঘ / A B C D / …) → `SheetTemplate.withSetField(name)` → `Store.addTemplate(updated)` + `settings.copy(template = updated)` + refresh. Also offers "Remove Set field" when one is set. |
| 11 | Rename Key tab headline → **"Teacher's Answer Sheet"** | `KeyTab.build()` passes `"Teacher's answer sheet"` to `tabPage(...)`. Bottom-nav label stays "Key". |
| 12 | **Multiple-bubble answer counts as wrong** | `Grader.grade()` maps `MarkStatus.MULTIPLE` → `Verdict.WRONG`. "Multiple" tile dropped from the Scan result and the Results detail sheet; "Multi" column dropped from Word/PDF/CSV reports. `SavedResult.multiple` kept on disk (old results) but hidden in UI; new ones write 0. `ResultImage` still draws the purple ring on multiple-filled questions. `GraderTest` updated. |

## Files touched (final commit `9dc9a99`)
- `app/src/main/java/com/omrscanner/app/HomeTab.kt` — thumbnail slot, Edit chip, Download-blank rows, print row removed
- `app/src/main/java/com/omrscanner/app/KeyTab.kt` — "Add a Set field" link + picker, remove-set-field link, title "Teacher's answer sheet"
- `app/src/main/java/com/omrscanner/app/SheetSetupActivity.kt` — "Something's missing" button + confirm-rows banner, scroll preservation on review rebuild
- `app/src/main/java/com/omrscanner/app/SheetPrinter.kt` — print() dropped, sharePdf() kept
- `app/src/main/java/com/omrscanner/app/MainActivity.kt` — same-tab scroll preservation in `show()`
- `app/src/main/java/com/omrscanner/app/Ui.kt` — `findScroll()` helper
- `app/src/main/java/com/omrscanner/app/ScanActivity.kt`, `ResultsTab.kt` — Multiple tile removed
- `app/src/main/java/com/omrscanner/app/TemplateThumbnail.kt` — new, vector thumbnail renderer with per-template cache
- `omr-core/src/main/kotlin/com/omrscanner/core/Templates.kt` — `SheetTemplate.withSetField(name)` helper
- `omr-core/src/main/kotlin/com/omrscanner/core/TemplateLearner.kt` — `classify()` NUMBER priority fix + exposed `classifyForTest`
- `omr-core/src/main/kotlin/com/omrscanner/core/Grader.kt` — MULTIPLE → Verdict.WRONG
- `omr-core/src/test/kotlin/com/omrscanner/core/TemplateLearnerTest.kt` — new `classifiesEveryKindOfField` test
- `omr-core/src/test/kotlin/com/omrscanner/core/GraderTest.kt` — expect WRONG for multiple-filled, wrong count = 2

## Verification
- `gradle compileKotlin --no-daemon -q` (scratchpad typecheck project) — clean
- `gradle -p omr-core test --no-daemon -q` — green
- CI job `omr-scanner-apk.yml` republishes the APK at `omr-scanner-latest`
