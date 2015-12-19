package info.voxtechnica.appraisers.task;

import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.ImmutableMultimap;
import info.voxtechnica.appraisers.db.dao.Licenses;
import info.voxtechnica.appraisers.model.License;
import info.voxtechnica.appraisers.util.TuidFactory;
import io.dropwizard.servlets.tasks.Task;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;

/**
 * Task: import ASC appraiser licenses from the specified tab-delimited text file with a header line. Files can be
 * downloaded from the <a href="https://www.asc.gov/Content/category1/st_data/v_Export_All.txt">ASC.gov web site</a>.
 * If you don't specify a day (format: YYYYMMDD), then the file name can be used to determine the download date
 * (format: asc.gov.2016-01-01.txt). If that's not available, then the current system time is used.
 */
public class ImportLicenseFileTask extends Task {
    // TODO: determine whether the EOF ^Z (\32) needs to be stripped from the text file outside Java
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

        // Identify the date assocated with the batch of records. Use current system time if millis == null
        Long millis = null;
        if (parameters.containsKey("day")) {
            // use the specified date
            LocalDate day = LocalDate.parse(parameters.get("day").asList().get(0), DateTimeFormatter.BASIC_ISO_DATE);
            millis = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } else if (fileName.contains("asc.gov.20")) {
            // use a date in the file name (format example: asc.gov.2016-01-01.txt.gz)
            int index = fileName.indexOf("asc.gov.20");
            LocalDate day = LocalDate.parse(fileName.substring(index + 8, index + 18), DateTimeFormatter.ISO_LOCAL_DATE);
            millis = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        }

        // Read and process lines (records) from the data file
        // TODO: use an executor service with a configured number of workers for importing data in parallel
        long count = 0;
        long countNew = 0;
        long countUpdated = 0;
        Path dataFile = Paths.get(fileName);
        try (BufferedReader reader = Files.newBufferedReader(dataFile, Charset.forName("Windows-1252"))) {
            String[] fieldNames = {};
            String line;
            while ((line = reader.readLine()) != null) {
                // convert the Windows-1252 String to a UTF-8 String
                line = new String(line.getBytes(), "UTF-8");
                // the first line is a header line
                if (++count == 1) fieldNames = line.trim().split("\t");
                else {
                    // capture raw appraiser license data
                    Map<String, String> rawData = new TreeMap<>();
                    String[] values = line.trim().split("\t");
                    for (int i = 0; i < values.length && i < fieldNames.length; i++)
                        rawData.put(fieldNames[i], values[i]);
                    // create an unidentified License
                    License newLicense = new License(rawData);
                    String newId = millis == null ? TuidFactory.getId() : TuidFactory.getIdFromTimestamp(millis + count);
                    // look for an existing license to update
                    License oldLicense = Licenses.readLicenseByAscKey(newLicense.getAscKey());
                    if (oldLicense != null) {
                        // if the license exists, but the raw data changed, then persist a new version
                        if (!rawData.equals(oldLicense.getRawData())) {
                            newLicense.setId(oldLicense.getId());
                            newLicense.setUpdateId(newId);
                            Licenses.createLicenseVersion(newLicense);
                            countUpdated++;
                        }
                    } else {
                        // create a new license
                        newLicense.setId(newId);
                        Licenses.createLicense(newLicense);
                        countNew++;
                    }
                }
                // Progress indicator
                if (count % 1000 == 0) {
                    long duration = System.currentTimeMillis() - startTime;
                    double rate = count == 0 ? 0.0 : (double) duration / (double) count;
                    printWriter.println(String.format("Processed: millis=%d rate=%,.1f records=%d new=%d updated=%d", duration, rate, count, countNew, countUpdated));
                    printWriter.flush();
                }
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        double rate = count == 0 ? 0.0 : (double) duration / (double) count;
        printWriter.println(String.format("Completed: millis=%d rate=%,.1f records=%d new=%d updated=%d", duration, rate, count, countNew, countUpdated));
        printWriter.close();
    }
}
