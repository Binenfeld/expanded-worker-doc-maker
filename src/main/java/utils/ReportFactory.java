package utils;

import models.WorkerSheet;
import utils.DocxWriter.Table;
import utils.DocxWriter.TableColors;

import java.io.IOException;
import java.nio.file.Path;
import java.text.Collator;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the Hebrew unassigned-workers report (.docx), on landscape pages so the sheet's many columns fit.
 *
 * <p>Document layout:
 * <pre>
 *   Title:      פרטי עובדים לא משוייכים
 *   Heading 1:  רשימת עובדים - סך הכל: amount
 *     Table:      the sheet as it is: every column, every worker, in the sheet's order          [blue]
 *   Heading 1:  סיכום                                                                 (new page)
 *     Heading 2:  עובדים לפי ארץ מוצא ומקצוע
 *     Table:      country | profession | number of workers, ending with the total row  [purple] (right half)
 * </pre>
 */
public final class ReportFactory {
    private static final String TITLE = "פרטי עובדים לא משוייכים";
    private static final String NOT_SPECIFIED = "לא צוין";
    private static final String WORKER_COUNT_HEADER = "מספר עובדים";
    private static final String TOTAL = "סך הכל";

    // Column widths of the worker table follow the longest text in each column, within these bounds
    // (in characters), so empty columns stay readable and long names don't squeeze the rest.
    private static final int MIN_COLUMN_WEIGHT = 6;
    private static final int MAX_COLUMN_WEIGHT = 24;

    // Hebrew alphabetical order (א-ת); handles spaces, punctuation and final letters properly.
    private static final Collator HEBREW_ORDER = Collator.getInstance(Locale.of("he", "IL"));

    private ReportFactory() {
    }

    /** Writes the report for the given sheet to {@code path}, overwriting any existing file. */
    public static void writeReport(WorkerSheet sheet, Path path) throws IOException {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        DocxWriter doc = new DocxWriter(true, true)
                .addTitle(TITLE)
                .addParagraph("נכון לתאריך " + today + "  |  " + TOTAL + " עובדים: " + sheet.rows().size());

        addWorkersSection(doc, sheet);
        addSummarySection(doc, sheet);

        doc.save(path);
    }

    // The sheet itself, unchanged: the header row is repeated at the top of every page.
    private static void addWorkersSection(DocxWriter doc, WorkerSheet sheet) {
        doc.addHeading(1, withTotal("רשימת עובדים", sheet.rows().size()));
        doc.addTable(TableColors.BLUE, sheet.headers(), sheet.rows(), null, columnWeights(sheet));
    }

    // How many workers there are of each profession from each country. Countries with the most workers come
    // first, and within a country, the professions with the most workers.
    private static void addSummarySection(DocxWriter doc, WorkerSheet sheet) {
        doc.addHeadingOnNewPage(1, "סיכום");

        List<String> countries = sheet.columnValues(WorkerSheet.COUNTRY_HEADER);
        List<String> professions = sheet.columnValues(WorkerSheet.PROFESSION_HEADER);
        Map<String, Map<String, Integer>> countryToProfessionCounts = new LinkedHashMap<>();
        for (int i = 0; i < countries.size(); i++) {
            countryToProfessionCounts
                    .computeIfAbsent(orNotSpecified(countries.get(i)), k -> new LinkedHashMap<>())
                    .merge(orNotSpecified(professions.get(i)), 1, Integer::sum);
        }
        Map<String, Integer> countryTotals = new LinkedHashMap<>();
        countryToProfessionCounts.forEach((country, counts) -> countryTotals.put(country, sum(counts)));

        List<List<String>> rows = new ArrayList<>();
        for (String country : sortByCountDescending(countryTotals).keySet()) {
            sortByCountDescending(countryToProfessionCounts.get(country)).forEach((profession, amount) ->
                    rows.add(List.of(country, profession, String.valueOf(amount))));
        }
        Table table = new Table("עובדים לפי ארץ מוצא ומקצוע", TableColors.PURPLE,
                List.of("ארץ מוצא", "מקצוע", WORKER_COUNT_HEADER), rows,
                List.of(TOTAL, "", String.valueOf(sheet.rows().size())), 3, 4, 2);
        doc.addSideBySide(table, null);
    }

    // ---------- Helpers ----------

    // One weight per column: the length of its longest value or header word, kept within the bounds.
    private static int[] columnWeights(WorkerSheet sheet) {
        int[] weights = new int[sheet.headers().size()];
        for (int c = 0; c < weights.length; c++) {
            int longest = 0;
            // A header can wrap between words, so only its longest word must fit.
            for (String word : sheet.headers().get(c).split("\\s+")) {
                longest = Math.max(longest, word.length());
            }
            for (List<String> row : sheet.rows()) {
                for (String line : row.get(c).split("\n")) {
                    longest = Math.max(longest, line.length());
                }
            }
            weights[c] = Math.clamp(longest, MIN_COLUMN_WEIGHT, MAX_COLUMN_WEIGHT);
        }
        return weights;
    }

    private static String orNotSpecified(String value) {
        return (value == null || value.isBlank()) ? NOT_SPECIFIED : value.trim();
    }

    private static int sum(Map<String, Integer> counts) {
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }

    // Largest count first; equal counts in Hebrew alphabetical order.
    private static Map<String, Integer> sortByCountDescending(Map<String, Integer> counts) {
        Map<String, Integer> sorted = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey(HEBREW_ORDER)))
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return sorted;
    }

    // "title - סך הכל: amount", used for every heading that shows a number of workers.
    private static String withTotal(String title, int amount) {
        return title + " - " + TOTAL + ": " + amount;
    }
}
