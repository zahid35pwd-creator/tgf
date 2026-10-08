# OMR Scanner (Android)

Reads a photo or scan of a filled-in OMR answer sheet and gives the result: the roll number,
each question's marked answer, and the score against your answer key (with optional negative marking).

## Download

Every push builds the app on GitHub Actions. On your phone, open the repository's **Releases** page,
choose **OMR Scanner (latest build)**, download `OMR-Scanner.apk` and open it. Android will ask you to
allow installing from your browser or file manager. The same release has blank sheets as PDFs to print.

## How to use

1. **Exam settings**: set the number of questions (1–100), 4 or 5 options, A B C D or ক খ গ ঘ letters,
   marks per correct answer and negative marks per wrong answer.
2. **Print sheets**: tap *Print blank OMR sheet* (print it, or save it as a PDF), or use the PDFs in
   [`sheets/`](sheets). Use A4 paper; any printer works.
3. **Answer key**: tap the right options, type or paste them (`ABDC…`), or scan one sheet filled in with the
   correct answers.
4. **Scan**: take a photo with *Scan sheet with camera*, or pick a scanned picture with *Choose scanned
   picture*. You can also share a picture to the app from the gallery or a scanner app.
5. The result screen shows the score, roll number and every answer, drawn over the flattened sheet:
   green = correct, red = wrong mark, orange = unanswered, purple = several marks. Tap *Save result* to
   keep it. *Saved results* exports everything as a CSV file for Excel or Google Sheets.

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
