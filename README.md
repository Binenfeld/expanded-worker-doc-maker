# ExpandedWorkerDocMaker

Turns the unassigned-workers sheet (`פרטי מידע לא משוייכים`, Excel) into a Hebrew Word report and a PDF copy of it.
A sibling of WorkerDocMaker, built the same way.

```
sheet.csv (exported from Excel) ──► WorkerParser ──► WorkerSheet ──► ReportFactory ──► פרטי עובדים לא משוייכים <date>.docx ──► PdfConverter ──► .pdf
```

## What the report contains

The report is right-to-left Hebrew on landscape A4 pages, with colored tables. Headings that show a number of
workers read `<title> - סך הכל: <amount>`.

| Page | Section | Content |
|---|---|---|
| 1 | Title + date line | `פרטי עובדים לא משוייכים`, today's date and the total number of workers |
| 1… | רשימת עובדים | The sheet as it is: every column and every worker, in the sheet's order. The header row repeats on every page |
| last | סיכום | Number of workers per country of origin and profession: countries with the most workers first, and within each country the professions with the most workers. Ends with the total row |

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
| מסד | Running number |
| מספר עובד | Worker ID |
| שם מלא | Full name (**required**) |
| מספר דרכון | Passport number |
| ארץ מוצא | Country of origin (**required**) |
| ותק בארץ | Time in Israel (`חודש`, `חצי שנה`, `שנתיים`…) |
| מקצוע | Profession (**required**) |
| שפות | Languages |
| רשיון נהיגה | Driving license |
| חווד על עובד | Opinion on the worker |
| היסטורית לקוחות | Client history |
| האם פוטר מאיפה ולמה | Was the worker fired, from where and why |

How it is read:

- **Columns are found by their header**, not their position. Only the three required columns must exist; the
  others are shown in the report as they are, so columns can be added, removed or reordered in the sheet. If a
  required column is renamed, update the header constants in `WorkerSheet`.
- **Rows without a name are left out.** That covers empty rows, and rows Excel exports with only a running
  number. A nameless row with other content is left out with a warning.
- **Columns with no header and nothing in them are dropped** (Excel often exports an empty one at the end).
- Cells are trimmed. A cell with several lines (Alt+Enter in Excel) keeps its line breaks in the report.
- An empty country or profession is counted under `לא צוין` in the summary.

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
    ├── DocxWriter.java       minimal .docx writer (titles, headings, RTL, landscape, colored tables) without libraries
    ├── PdfConverter.java     .docx → PDF through Word (PowerShell + COM); always closes the Word it starts
    ├── UserFacingException.java  a problem the user can fix; Main prints its message without a stack trace
    └── FileIOHandler.java    small UTF-8 file helpers
```

`DocxWriter` writes the Office Open XML parts by hand and zips them, so the project has no dependencies.
Headings use Word's built-in Heading styles, so they appear in Word's navigation pane.

## License

Copyright (C) 2026 Binenfeld

This program is free software: you can redistribute it and/or modify it under the terms of the
[GNU General Public License](LICENSE) as published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version. It is distributed WITHOUT ANY WARRANTY; see the license for details.
