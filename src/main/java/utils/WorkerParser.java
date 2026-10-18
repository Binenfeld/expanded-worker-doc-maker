package utils;

import models.WorkerSheet;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.file.FileSystemException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reads the unassigned-workers sheet ("פרטי מידע לא משוייכים"), exported from Excel as "CSV UTF-8".
 *
 * <p>The first row is the header; every other row is one worker. The sheet's columns, as of writing:
 * <pre>
 *   מסד | מספר עובד | שם מלא | מספר דרכון | ארץ מוצא | ותק בארץ | מקצוע | שפות | רשיון נהיגה |
 *   חווד על עובד | היסטורית לקוחות | האם פוטר מאיפה ולמה
 * </pre>
 * Columns are found by their header, not their position, so they can be reordered and others can be added.
 * Only {@value WorkerSheet#NAME_HEADER}, {@value WorkerSheet#COUNTRY_HEADER} and
 * {@value WorkerSheet#PROFESSION_HEADER} must exist. Only the columns in {@link #SHOWN_HEADERS} are kept for the
 * report, in the sheet's order; the name is used to tell workers from empty rows, but is not shown.
 */
public final class WorkerParser {
    private static final List<String> REQUIRED_HEADERS =
            List.of(WorkerSheet.NAME_HEADER, WorkerSheet.COUNTRY_HEADER, WorkerSheet.PROFESSION_HEADER);

    // The columns shown in the report; any other column is left out, even when filled in.
    // Compared without quotes, so "חוו""ד על עובד" also matches.
    private static final List<String> SHOWN_HEADERS = List.of("מסד", "מספר דרכון", WorkerSheet.COUNTRY_HEADER,
            "ותק בארץ", WorkerSheet.PROFESSION_HEADER, "שפות", "רשיון נהיגה", "חווד על עובד", "היסטורית לקוחות",
            "האם פוטר מאיפה ולמה");

    // Excel starts a UTF-8 CSV with this invisible character.
    private static final String BYTE_ORDER_MARK = String.valueOf((char) 0xFEFF);

    private WorkerParser() {
    }

    /**
     * Returns the sheet's header and worker rows. Rows without a name are skipped: empty rows, and rows Excel
     * exported because a cell further down was once formatted. Only the shown columns are kept.
     */
    public static WorkerSheet parseWorkerCsv(Path csvFile) throws IOException, UserFacingException {
        List<List<String>> records = parseCsv(readText(csvFile));
        if (records.isEmpty() || records.getFirst().stream().allMatch(String::isEmpty)) {
            throw new UserFacingException(csvFile + " is empty, or its first row is. Export the sheet again,"
                    + " with the header in the first row.");
        }
        List<String> header = records.getFirst();
        checkHeader(csvFile, header);

        int nameColumn = header.indexOf(WorkerSheet.NAME_HEADER);
        List<List<String>> rows = new ArrayList<>();
        for (int i = 1; i < records.size(); i++) {
            List<String> row = padded(records.get(i), header.size());
            if (row.get(nameColumn).isEmpty()) {
                long filled = row.stream().filter(cell -> !cell.isEmpty()).count();
                // A lone number is just the running number (מסד) of an unused row.
                if (filled > 1) {
                    System.out.println("Warning: row " + (i + 1) + " has no name (" + WorkerSheet.NAME_HEADER
                            + ") and is left out: " + String.join(" | ", row));
                }
                continue;
            }
            rows.add(row);
        }
        if (rows.isEmpty()) {
            throw new UserFacingException(csvFile + " has the sheet's header but no workers (no row has a name in"
                    + " the \"" + WorkerSheet.NAME_HEADER + "\" column). Check that the right sheet was exported.");
        }
        return keepShownColumns(header, rows);
    }

    private static String readText(Path csvFile) throws IOException, UserFacingException {
        try {
            return new FileIOHandler().readString(csvFile).replace(BYTE_ORDER_MARK, "");
        } catch (MalformedInputException e) {
            throw new UserFacingException(csvFile + " is not saved as UTF-8, so its Hebrew text cannot be read."
                    + " In Excel, save the sheet again with File > Save As > \"CSV UTF-8 (Comma delimited)\""
                    + " (not the plain \"CSV (Comma delimited)\").", e);
        } catch (FileSystemException e) {
            throw new UserFacingException("Cannot read " + csvFile + ": " + Objects.requireNonNullElse(e.getReason(), e.toString())
                    + ". If it is open in another program, close it and run again.", e);
        }
    }

    // Makes sure this is the unassigned-workers sheet: its main columns must be there.
    private static void checkHeader(Path csvFile, List<String> header) throws UserFacingException {
        List<String> missing = REQUIRED_HEADERS.stream().filter(h -> !header.contains(h)).toList();
        if (!missing.isEmpty()) {
            throw new UserFacingException(csvFile + " does not look like the unassigned-workers sheet: the first row"
                    + " has no " + String.join(", ", missing.stream().map(h -> "\"" + h + "\"").toList())
                    + " column (found: " + String.join(", ", header) + "). Check that the right sheet was exported,"
                    + " with the header in the first row. If a column was renamed, update the header constants in"
                    + " WorkerSheet.");
        }
    }

    // Keeps only the shown columns, in the sheet's order. A shown column missing from the sheet gets a warning.
    private static WorkerSheet keepShownColumns(List<String> header, List<List<String>> rows) {
        List<String> shown = SHOWN_HEADERS.stream().map(WorkerParser::withoutQuotes).toList();
        List<String> found = header.stream().map(WorkerParser::withoutQuotes).toList();
        for (int i = 0; i < shown.size(); i++) {
            if (!found.contains(shown.get(i))) {
                System.out.println("Warning: the sheet has no \"" + SHOWN_HEADERS.get(i) + "\" column, so the report"
                        + " leaves it out. If it was renamed, update SHOWN_HEADERS in WorkerParser.");
            }
        }
        List<Integer> kept = new ArrayList<>();
        for (int c = 0; c < header.size(); c++) {
            if (shown.contains(found.get(c))) {
                kept.add(c);
            }
        }
        List<String> keptHeader = kept.stream().map(header::get).toList();
        List<List<String>> keptRows = rows.stream().map(row -> kept.stream().map(row::get).toList()).toList();
        return new WorkerSheet(keptHeader, keptRows);
    }

    // A header without double or single quotes, so חוו"ד and חווד compare equal.
    private static String withoutQuotes(String header) {
        return header.replace("\"", "").replace("'", "");
    }

    // The row with exactly the given number of cells: shorter rows are filled with "", longer ones cut.
    private static List<String> padded(List<String> row, int size) {
        List<String> result = new ArrayList<>(row.subList(0, Math.min(row.size(), size)));
        while (result.size() < size) {
            result.add("");
        }
        return result;
    }

    /**
     * Splits CSV text into records of trimmed fields. Commas and line breaks inside double quotes are part of
     * the field (a free-text cell can hold several lines), and a doubled quote inside quotes ("חוו""ד") is read
     * as a single quote character.
     */
    private static List<List<String>> parseCsv(String text) {
        List<List<String>> records = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else if (c == '"') {
                    inQuotes = false;
                } else if (c != '\r') {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(current.toString().strip());
                current.setLength(0);
            } else if (c == '\n') {
                fields.add(current.toString().strip());
                current.setLength(0);
                records.add(fields);
                fields = new ArrayList<>();
            } else if (c != '\r') {
                current.append(c);
            }
        }
        // The last record, unless the file ends with a line break.
        if (!current.isEmpty() || !fields.isEmpty()) {
            fields.add(current.toString().strip());
            records.add(fields);
        }
        return records;
    }
}
