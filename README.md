# ExpandedWorkerDocMaker

Turns the unassigned-workers sheet (`פרטי מידע לא משוייכים`, Excel) into a Hebrew Word report and a PDF copy of it.
A sibling of WorkerDocMaker, built the same way.

```
sheet.csv (exported from Excel) ──► WorkerParser ──► WorkerSheet ──► ReportFactory ──► פרטי עובדים לא משוייכים <date>.docx ──► PdfConverter ──► .pdf
```

## What the report contains

The report is right-to-left Hebrew on landscape A4 pages, with a 9pt table font so as much as possible fits on a
page. The worker table fills the page width: columns with values are only as wide as their longest value or header
word, and the empty columns share the rest of the width, so there is room to fill them in on the printout (if no
column is empty, all columns share it).

| Page | Section | Content |
|---|---|---|
| 1 | Title + date line | `פרטי עובדים לא משוייכים`, today's date and the total number of workers |
| 1… | רשימת עובדים | Every worker, in the sheet's order, with the columns marked "shown" below. The header row repeats on every page |

## Requirements

- JDK 21 or newer (the project is developed with JDK 26)
- Gradle, through the included wrapper (`gradlew`); no external libraries are needed
- Excel, to export the sheet to CSV
- Microsoft Word (desktop), to convert the report to PDF. `PdfConverter` drives it through PowerShell, so this
  needs Windows. Without Word the run fails after the .docx is written.

## Running it

Step-by-step instructions in Hebrew for non-technical users: [`הוראות הפעלה.txt`](הוראות%20הפעלה.txt).

1. **Export the sheet.** Open the unassigned-workers workbook and use File → Save As →
   **CSV UTF-8 (Comma delimited)**. Save it in `C:\expandedworkerdocmaker` under any name ending in `.csv`. It
   must be the UTF-8 variant, or the Hebrew text will be garbled.
2. **Build** (once, and again after code changes):
   ```
   powershell -ExecutionPolicy Bypass -File scripts\build.ps1
   ```
   This produces `build\libs\ExpandedWorkerDocMaker.jar`. The script uses `JAVA_HOME` if it is set, otherwise the
   newest JDK in `~\.jdks` (where IntelliJ downloads JDKs).
3. **Run:**
   ```
   powershell -ExecutionPolicy Bypass -File scripts\run.ps1
   ```
   Or just **double-click `scripts\run.cmd`**, which does the same and keeps the window open to show the result.
   You can also run `Main` from IntelliJ. Options:
   - `-InputFolder D:\some\folder`: use a different folder
   - `-Debug`: also print every row read, and the full stack trace of an error
4. The program reads the **newest** `.csv` in the folder and writes the report next to it as
   `פרטי עובדים לא משוייכים <yyyy-mm-dd>.docx`, plus a PDF copy (`.pdf`, same name) made with Word.

The default input folder is `Main.DEFAULT_INPUT_FOLDER`. It is a different folder from WorkerDocMaker's
(`C:\workerdocmaker`), because both programs take the newest CSV in their folder. The jar takes the same options
directly: `java -jar ExpandedWorkerDocMaker.jar [folder] [--debug]`. Excel lock files (`~$…`) are ignored.
`-ExecutionPolicy Bypass` is only needed if Windows blocks running scripts.

### When something goes wrong

The run stops with exit code 1 and a line starting with `ERROR:` that says what is wrong and how to fix it. For
example, it reports a missing folder or CSV, a CSV saved as plain CSV instead of CSV UTF-8, a file that isn't
the unassigned-workers sheet, a sheet without workers, a report left open in Word, and a missing Word install.
`run.cmd` then shows "Something went wrong". An `ERROR: Unexpected error` comes with a stack trace; that one is a
bug or something unusual on the computer.

## Input format

The first row is the header, and every other row is one worker. The sheet's columns, as of writing:

| Header | Meaning |
|---|---|
| מסד | Running number (**shown**) |
| מספר עובד | Worker ID (not shown) |
| שם מלא | Full name (**required**; not shown, but tells workers from empty rows) |
| מספר דרכון | Passport number (**shown**) |
| ארץ מוצא | Country of origin (**required, shown**) |
| ותק בארץ | Time in Israel (`חודש`, `חצי שנה`, `שנתיים`…) (**shown**) |
| מקצוע | Profession (**required, shown**) |
| שפות | Languages (**shown**, usually empty) |
| רשיון נהיגה | Driving license (**shown**, usually empty) |
| חווד על עובד | Opinion on the worker (**shown**, usually empty) |
| היסטורית לקוחות | Client history (**shown**, usually empty) |
| האם פוטר מאיפה ולמה | Was the worker fired, from where and why (**shown**, usually empty) |

How it is read:

- **Columns are found by their header**, not their position, so they can be reordered and new ones added. Only
  the three required columns must exist. If one of them is renamed, update the header constants in `WorkerSheet`.
- **Only the columns marked "shown" are in the report**, in the sheet's order; any other column is left out, even
  when filled in. The list is `SHOWN_HEADERS` in `WorkerParser` (quotes are ignored when comparing, so `חוו"ד`
  matches `חווד`). A shown column missing from the sheet is left out with a warning.
- **Rows without a name are left out.** That covers empty rows, and rows Excel exports with only a running
  number. A nameless row with other content is left out with a warning.
- Cells are trimmed. A cell with several lines (Alt+Enter in Excel) keeps its line breaks in the report.

## Project structure

```
scripts/
├── build.ps1                 builds build\libs\ExpandedWorkerDocMaker.jar
├── run.ps1                   runs the jar on the newest CSV in C:\expandedworkerdocmaker
└── run.cmd                   double-click launcher for run.ps1
src/main/java/
├── Main.java                 entry point: newest CSV → parse → write report → PDF
├── models/
│   └── WorkerSheet.java      the sheet: header + one row per worker, and the required header names
└── utils/
    ├── WorkerParser.java     reads the CSV into a WorkerSheet
    ├── ReportFactory.java    lays out the report (sections, tables, counting, sorting)
    ├── DocxWriter.java       minimal .docx writer (titles, headings, RTL, landscape, colored and fitted tables) without libraries
    ├── PdfConverter.java     .docx → PDF through Word (PowerShell + COM); always closes the Word it starts
    ├── UserFacingException.java  a problem the user can fix; Main prints its message without a stack trace
    └── FileIOHandler.java    small UTF-8 file helpers
```

`DocxWriter` writes the Office Open XML parts by hand and zips them, so the project has no dependencies.
Headings use Word's built-in Heading styles, so they appear in Word's navigation pane.

## License

Copyright (C) 2026 Binenfeld. All rights reserved.

Proprietary and confidential: no one may access, use, copy, modify or distribute this software without written
permission from Binenfeld. See [LICENSE](LICENSE).
