package info.voxtechnica.appraisers.task;

import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.ImmutableMultimap;
import info.voxtechnica.appraisers.db.dao.Events;
import info.voxtechnica.appraisers.service.LicenseService;
import io.dropwizard.servlets.tasks.Task;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.Charset;

/**
 * Task: import appraiser licenses from the <a href="https://www.asc.gov/Content/category1/st_data/v_Export_All.txt">ASC.gov web site</a>.
 * The downloaded text file contains tab-delimited text with a header line. It's a complete data snapshot from ASC.gov.
 * <p/>
 * Usage: curl -X POST http://localhost:8081/tasks/import-licenses
 */
public class ImportLicensesTask extends Task {
    private final HttpClient http;

    public ImportLicensesTask(HttpClient httpClient) {
        super("import-licenses");
        http = httpClient;
    }

    @Override
    @Timed
    public void execute(ImmutableMultimap<String, String> parameters, PrintWriter printWriter) throws Exception {
        long startTime = System.currentTimeMillis();
        String message;
        HttpGet request = new HttpGet("https://www.asc.gov/Content/category1/st_data/v_Export_All.txt");
        HttpResponse response = http.execute(request);
        if (200 == response.getStatusLine().getStatusCode()) {
            InputStreamReader inputStreamReader = new InputStreamReader(response.getEntity().getContent(), Charset.forName("Windows-1252"));
            BufferedReader reader = new BufferedReader(inputStreamReader);
            message = LicenseService.importLicenses(reader, null);
        } else {
            message = String.format("ImportLicensesTask: Error %d fetching database snapshot", response.getStatusLine().getStatusCode());
            Events.error(message, null);
        }
        printWriter.println(String.format("%s from ASC.gov in %d ms", message, System.currentTimeMillis() - startTime));
        printWriter.close();
    }
}
