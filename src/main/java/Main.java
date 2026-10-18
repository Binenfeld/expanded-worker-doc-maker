import models.WorkerSheet;
import utils.FileIOHandler;
import utils.PdfConverter;
import utils.ReportFactory;
import utils.UserFacingException;
import utils.WorkerParser;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads the newest unassigned-workers CSV in the input folder, writes the Hebrew report (.docx) next to it,
 * and converts it to PDF with Word.
 *
 * <p>Usage: {@code java -jar ExpandedWorkerDocMaker.jar [inputFolder] [--debug]} (default folder
 * {@value #DEFAULT_INPUT_FOLDER}). {@code --debug} also prints every row read, and the stack trace of any error.
 *
 * <p>Any problem ends the program with exit code 1 and a message saying what went wrong and how to fix it.
 */
public class Main {
    // Where the unassigned-workers sheet, exported from Excel as "CSV UTF-8", is dropped. The newest .csv there is used.
    private static final String DEFAULT_INPUT_FOLDER = "C:\\expandedworkerdocmaker";

    private static final String DEBUG_FLAG = "--debug";

    public static void main(String[] args) {
        boolean debug = List.of(args).contains(DEBUG_FLAG);
        try {
            run(args, debug);
        } catch (UserFacingException e) {
            printError(e.getMessage(), e, debug);
            System.exit(1);
        } catch (Throwable e) {
            // Not one of the problems the program knows how to explain: a bug, or something unusual on this computer.
            printError("Unexpected error: " + describe(e), e, true);
            System.exit(1);
        }
    }

    private static void run(String[] args, boolean debug) throws Exception {
        List<String> folders = new ArrayList<>();
        for (String arg : args) {
            if (arg.equals(DEBUG_FLAG)) continue;
            if (arg.startsWith("-")) {
                throw new UserFacingException("Unknown option \"" + arg + "\". Options: " + DEBUG_FLAG + ".");
            }
            folders.add(arg);
        }
        if (folders.size() > 1) {
            throw new UserFacingException("Expected at most one input folder, got " + folders.size() + ": " + folders
                    + ". Put a folder name that contains spaces in quotes.");
        }
        Path inputFolder = Path.of(folders.isEmpty() ? DEFAULT_INPUT_FOLDER : folders.getFirst()).toAbsolutePath();
        if (!Files.isDirectory(inputFolder)) {
            throw new UserFacingException("The input folder " + inputFolder + " does not exist. Create it and save"
                    + " the unassigned-workers sheet there (Excel: File > Save As > CSV UTF-8), or pass a different folder.");
        }

        Path inputCsv = findInputCsv(inputFolder);
        System.out.println("Reading " + inputCsv);
        WorkerSheet sheet = WorkerParser.parseWorkerCsv(inputCsv);
        System.out.println("Read " + sheet.rows().size() + " workers, " + sheet.headers().size() + " columns");
        if (debug) {
            System.out.println(String.join(" | ", sheet.headers()));
            sheet.rows().forEach(row -> System.out.println(String.join(" | ", row)));
        }

        String reportName = "פרטי עובדים לא משוייכים " + LocalDate.now();
        Path report = inputCsv.resolveSibling(reportName + ".docx");
        try {
            ReportFactory.writeReport(sheet, report);
        } catch (FileSystemException e) {
            throw new UserFacingException("Cannot write " + report + " - it is probably open in Word."
                    + " Close it and run again. (" + Objects.requireNonNullElse(e.getReason(), e.toString()) + ")", e);
        } catch (IOException e) {
            throw new UserFacingException("Cannot write " + report + ": " + describe(e)
                    + ". Check that the folder is writable and the disk is not full.", e);
        }
        System.out.println("Report written to " + report);

        Path pdf = inputCsv.resolveSibling(reportName + ".pdf");
        try {
            PdfConverter.convert(report, pdf);
        } catch (IOException e) {
            throw new UserFacingException(e.getMessage(), e);
        }
        System.out.println("PDF written to " + pdf);
    }

    private static Path findInputCsv(Path inputFolder) throws UserFacingException, IOException {
        FileIOHandler files = new FileIOHandler();
        Optional<Path> csv = files.newestFile(inputFolder, ".csv");
        if (csv.isPresent()) {
            return csv.get();
        }
        boolean hasWorkbook = files.newestFile(inputFolder, ".xlsb").isPresent()
                || files.newestFile(inputFolder, ".xlsx").isPresent();
        throw new UserFacingException("No .csv file in " + inputFolder + "."
                + (hasWorkbook ? " There is an Excel workbook there, but the program reads CSV only." : "")
                + " Open the workbook, select the unassigned-workers sheet, and use File > Save As > \"CSV UTF-8"
                + " (Comma delimited)\" to save it in this folder.");
    }

    private static void printError(String message, Throwable e, boolean withStackTrace) {
        System.out.flush();
        System.err.println();
        System.err.println("ERROR: " + message);
        if (withStackTrace) {
            System.err.println();
            e.printStackTrace();
        }
        System.err.flush();
    }

    // "message (ExceptionType)", or just the type when there is no message (e.g. a NullPointerException).
    private static String describe(Throwable e) {
        String type = e.getClass().getSimpleName();
        return e.getMessage() == null || e.getMessage().isBlank() ? type : e.getMessage() + " (" + type + ")";
    }
}
