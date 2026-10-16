# OMR Scanner (Android)

Reads a photo or scan of a filled-in OMR answer sheet and gives the result: the roll number,
each question's marked answer, and the score against your answer key (with optional negative marking).

## Supported sheets

Choose the sheet on the **Home** tab:

| Sheet | Questions | Also reads |
| --- | --- | --- |
| 100 MCQ OMR sheet (print it from the app or `sheets/`) | up to 100, 4–5 options | roll number |
| BCS preliminary (BPSC, pink) | 200, ক–ঘ | registration, set, BCS and centre codes |
| SSC/HSC education board (pink) | 50, ক–ঘ | roll, set code, registration, subject code |

On the pink board sheets the reader ignores the pink print, including the letters inside the bubbles, and only counts black or blue pen.
Answer keys can be entered separately for each set (ক খ গ ঘ, or 0–9 for BCS); sheets with a set that has no key of its own use the "All sets" key.

### Any other OMR sheet

On the **Home** tab, **Add a new OMR sheet**, give the app a **blank** copy of the sheet (PDF, scan or photo).
It finds every bubble and works out the answer blocks, number fields (roll, registration…) and letter codes (set).
On the review screen you can check them coloured on the sheet with question numbers, change a field's type, name the fields,
mark which is the roll / ID and which is the set, and choose the question order and option letters. The saved sheet type then works
everywhere: single scans, PDF batches and Word reports.

Sheets with black corner squares are lined up by the squares. Sheets without them are lined up by the edges of the paper,
so photograph them on a darker surface with the whole page in view (flat scans and PDF pages work as they are).

## Many sheets at once (PDF → Word)

On the **Scanner** tab choose **PDF**, or share a PDF to the app. Every page is checked, and you get a list sorted
by roll / registration number. Save or share it as a **Word (.docx) report**. The report has a summary table (ID, set,
score, right / wrong / blank) and each candidate's answers, with the correct option shown next to every mistake.

## Download

Every push builds the app on GitHub Actions. On your phone, open the repository's **Releases** page,
choose **OMR Scanner (latest build)**, download `OMR-Scanner.apk` and open it. Android will ask you to
allow installing from your browser or file manager. The same release has blank sheets as PDFs to print.

## How to use

Four tabs: **Home**, **Key**, **Scanner** and **Results**. App settings (light / dark mode, share, about) are in the ⋮ menu.

1. **Home**: tap the active exam to set its name, number of questions, 4 or 5 options, A B C D or ক খ গ ঘ letters and marking.
   Choose the OMR sheet: a ready-made one only if your sheet is exactly the same design, otherwise **Add a new OMR sheet**
   from a blank copy. Tools: download or print this app's blank sheet.
2. **Key**: enter the answer key for the selected sheet (and each question set): scan a filled key with the camera, a photo or a PDF,
   type / paste it (`ABDC…`), or tap the answers.
3. **Scanner**: scan from the camera, a photo or a PDF. A PDF with several pages is checked page by page into a report.
4. **Results**: results grouped by exam. Open an exam for its summary, each student's result, and Word / Excel (CSV) / PDF export.

Rules: one bubble per question. A blank question gets 0. A wrong answer, or several bubbles filled in,
loses the negative mark. Faint or erased marks are flagged (⚠) for you to check.

### Getting a good picture

- All 4 black corner squares must be in the picture.
- Let the sheet fill most of the photo and lie reasonably flat. Any angle or rotation (even upside down) is fine.
- Avoid strong shadows or glare across the bubbles.

## How it works

The engine (`omr-core`) is plain Kotlin with no dependencies. It runs fully on the phone; nothing is uploaded.

1. Shrinks the photo and evens out the lighting by dividing by an estimate of the local paper brightness.
2. Finds solid, square dark blobs: candidates for the corner markers. Filled bubbles are round, so they don't count.
3. Tries the largest groups of 4 candidates in each rotation. It keeps the one where the orientation bar,
   the blank margins, the marker sizes and the printed roll-number circles all line up. This gives a
   perspective transform from the sheet to the photo.
4. Fine-aligns each block and row of bubbles to the printed circles, to cope with bent paper.
5. Measures how dark the inside of every bubble is. A threshold that adapts to the photo then decides
   which bubbles are filled.

Tests in `omr-core/src/test` render sheets, fill them like a student would (pen or pencil), then
"photograph" them with perspective, rotation, bent paper, shadows, blur, noise and JPEG compression, and
check every answer. Very harsh photos (1 MP, heavy blur) may be rejected with a message, but they are
never misread without warning.

## Building

```
./gradlew -p omr-core test        # engine tests (any JDK 17+, no Android SDK needed)
./gradlew :app:assembleRelease    # APK (needs the Android SDK), output in app/build/outputs/apk/release/
```

Or open this folder in Android Studio. The app has no library dependencies: it uses only the Android framework.
Builds are signed with `app/signing.p12` (password `android`), so new builds install over old ones.
Replace it with your own private key before publishing on Google Play.

## Project layout

| Path | What |
| --- | --- |
| `omr-core/src/main/kotlin` | Sheet layout, sheet drawing, image processing, reader, grading |
| `omr-core/src/test/kotlin` | Simulated sheets and photos, and the tests that use them |
| `app/src/main/java` | Android screens: home, scan/result, answer key, saved results, printing |
| `sheets/` | Ready-to-print blank sheets (A4 PDF) |
