package info.voxtechnica.appraisers.task;

import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.ImmutableMultimap;
import info.voxtechnica.appraisers.service.LicenseService;
import io.dropwizard.servlets.tasks.Task;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Task: import ASC appraiser licenses from the specified tab-delimited text file with a header line. Files can be
 * downloaded from the <a href="https://www.asc.gov/Content/category1/st_data/v_Export_All.txt">ASC.gov web site</a>.
 * If you don't specify a day (format: YYYYMMDD), then the file name can be used to determine the download date
 * (format: asc.gov.2016-01-01.txt). If that's not available, then the current system time is used.
 */
public class ImportLicenseFileTask extends Task {
    // TODO: determine whether the EOF ^Z (\32) needs to be stripped from the text file prior to import
    private final String usage = "curl --data 'file=/path/to/data/file.txt' --data 'day=20151018' http://localhost:8081/tasks/import-license-file";

    public ImportLicenseFileTask() {
        super("import-license-file");
    }

    @Override
    @Timed
    public void execute(ImmutableMultimap<String, String> parameters, PrintWriter printWriter) throws Exception {
        long startTime = System.currentTimeMillis();

        // Identify the data file to import
        String fileName;
        if (parameters.containsKey("file")) {
            fileName = parameters.get("file").asList().get(0);
            printWriter.println("Importing licenses from file: " + fileName);
            printWriter.flush();
        } else {
            printWriter.print(String.format("Example Usage: %s\nError: missing file name.\n", usage));
            return;
        }

        // Identify the date assocated with the batch of records
        LocalDate day = null; // if null, use current system time instead of an historical date
        if (parameters.containsKey("day")) {
            // use the specified date
            day = LocalDate.parse(parameters.get("day").asList().get(0), DateTimeFormatter.BASIC_ISO_DATE);
        } else if (fileName.contains("asc.gov.20")) {
            // use a date in the file name (format example: asc.gov.2016-01-01.txt.gz)
            int index = fileName.indexOf("asc.gov.20");
            day = LocalDate.parse(fileName.substring(index + 8, index + 18), DateTimeFormatter.ISO_LOCAL_DATE);
        }

        // Read and process lines (records) from the data file
        Path dataFile = Paths.get(fileName);
        BufferedReader reader = Files.newBufferedReader(dataFile, Charset.forName("Windows-1252"));
        long count = LicenseService.importLicenses(reader, day);

        printWriter.println(String.format("Complete. Enqueued %d license records in %d ms.", count, System.currentTimeMillis() - startTime));
        printWriter.close();
    }
}
