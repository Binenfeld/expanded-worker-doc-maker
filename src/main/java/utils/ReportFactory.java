package utils;

import models.WorkerSheet;
import utils.DocxWriter.TableColors;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the Hebrew unassigned-workers report (.docx), on landscape pages.
 *
 * <p>Document layout:
 * <pre>
 *   Title:      פרטי עובדים לא משוייכים
 *   Heading 1:  רשימת עובדים - סך הכל: amount
 *     Table:      the shown columns of the sheet (see WorkerParser), every worker, in the sheet's order  [blue]
 * </pre>
 * The table fills the page width. Columns with values are only as wide as their contents, and the empty columns share
 * the rest, so there is room to fill them in on the printout. When no column is empty, all of them share it.
 */
public final class ReportFactory {
    private static final String TITLE = "פרטי עובדים לא משוייכים";
    private static final String TOTAL = "סך הכל";

    private ReportFactory() {
    }

    /** Writes the report for the given sheet to {@code path}, overwriting any existing file. */
    public static void writeReport(WorkerSheet sheet, Path path) throws IOException {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        DocxWriter doc = new DocxWriter(true, true)
                .addTitle(TITLE)
                .addParagraph("נכון לתאריך " + today + "  |  " + TOTAL + " עובדים: " + sheet.rows().size());

        addWorkersSection(doc, sheet);

        doc.save(path);
    }

    // The sheet's shown columns; the header row is repeated at the top of every page.
    private static void addWorkersSection(DocxWriter doc, WorkerSheet sheet) {
        doc.addHeading(1, withTotal("רשימת עובדים", sheet.rows().size()));
        doc.addFittedTable(null, TableColors.BLUE, sheet.headers(), sheet.rows(), null, columnsToStretch(sheet));
    }

    // The columns with no value in any row, or every column if none is empty.
    private static List<Integer> columnsToStretch(WorkerSheet sheet) {
        List<Integer> empty = new ArrayList<>();
        List<Integer> all = new ArrayList<>();
        for (int c = 0; c < sheet.headers().size(); c++) {
            int column = c;
            all.add(c);
            if (sheet.rows().stream().allMatch(row -> row.get(column).isEmpty())) {
                empty.add(c);
            }
        }
        return empty.isEmpty() ? all : empty;
    }

    // "title - סך הכל: amount", used for every heading that shows a number of workers.
    private static String withTotal(String title, int amount) {
        return title + " - " + TOTAL + ": " + amount;
    }
}
