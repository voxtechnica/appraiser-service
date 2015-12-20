package info.voxtechnica.appraisers.service;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import info.voxtechnica.appraisers.db.dao.Events;
import info.voxtechnica.appraisers.util.LicenseImporter;
import info.voxtechnica.appraisers.util.TuidFactory;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.ExecutorService;

public class LicenseService {
    private static final Logger LOG = LoggerFactory.getLogger(LicenseService.class);
    private static boolean initialized = false;
    private static ListeningExecutorService importService;

    public static void initialize(ExecutorService executorService) {
        if (!initialized) {
            importService = MoreExecutors.listeningDecorator(executorService);
            initialized = true;
        } else LOG.info("Previously initialized.");
    }

    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Import ASC appraiser licenses from an ASC license data snapshot. Licenses are imported using concurrent workers.
     *
     * @param reader Buffered reader
     * @param day    Optional local date used for importing historical data
     * @return number of licenses imported
     */
    public static long importLicenses(BufferedReader reader, LocalDate day) {
        long count = 0;
        try {
            Long millis = day == null ? null : day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            String[] fieldNames = {};
            String line;
            while ((line = reader.readLine()) != null) {
                // convert the Windows-1252 String to a UTF-8 String
                line = new String(line.getBytes(), "UTF-8");
                // the first line is a header line
                if (++count == 1) fieldNames = line.trim().split("\t");
                else {
                    String id = millis == null ? TuidFactory.getId() : TuidFactory.getIdFromTimestamp(millis + count);
                    importService.submit(new LicenseImporter(fieldNames, line, id));
                }
            }
        } catch (Exception e) {
            Events.error("LicenseService error: " + ExceptionUtils.getRootCauseMessage(e), ExceptionUtils.getStackTrace(e));
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
                Events.error("LicenseService error: " + ExceptionUtils.getRootCauseMessage(e), ExceptionUtils.getStackTrace(e));
            }
        }
        return count - 1; // number of license records
    }
}
