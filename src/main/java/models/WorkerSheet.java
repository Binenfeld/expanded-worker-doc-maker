package models;

import java.util.List;

/**
 * The unassigned-workers sheet as read from the CSV: its header row and one row per worker, in the sheet's order.
 * Every row has exactly one cell per header; cells are trimmed and never null.
 */
public record WorkerSheet(List<String> headers, List<List<String>> rows) {
    // Header texts of the columns the summary needs.
    public static final String NAME_HEADER = "שם מלא";
    public static final String COUNTRY_HEADER = "ארץ מוצא";
    public static final String PROFESSION_HEADER = "מקצוע";

    public WorkerSheet {
        headers = List.copyOf(headers);
        rows = rows.stream().map(List::copyOf).toList();
    }

    /** The 0-based index of the column with this header, or -1 if the sheet has no such column. */
    public int column(String header) {
        return headers.indexOf(header);
    }

    /** The value in the given column of every row, in row order. */
    public List<String> columnValues(String header) {
        int column = column(header);
        if (column < 0) {
            throw new IllegalArgumentException("The sheet has no \"" + header + "\" column");
        }
        return rows.stream().map(row -> row.get(column)).toList();
    }

    @Override
    public String toString() {
        return "WorkerSheet[" + headers.size() + " columns, " + rows.size() + " workers]";
    }
}
