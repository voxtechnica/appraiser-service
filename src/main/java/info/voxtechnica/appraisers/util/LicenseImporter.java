package info.voxtechnica.appraisers.util;

import info.voxtechnica.appraisers.db.dao.Events;
import info.voxtechnica.appraisers.db.dao.Imports;
import info.voxtechnica.appraisers.db.dao.Licenses;
import info.voxtechnica.appraisers.model.License;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.Map;
import java.util.TreeMap;

/**
 * Import an ASC appraiser license as tab-delimited text. Files can be downloaded from the
 * <a href="https://www.asc.gov/Content/category1/st_data/v_Export_All.txt">ASC.gov web site</a>.
 */
public class LicenseImporter implements Runnable {
    private final String importId;
    private final Integer day;
    private final String[] fieldNames;
    private final String record;
    private final String id;

    public LicenseImporter(String importId, Integer day, String[] fieldNames, String record, String id) {
        this.importId = importId;
        this.day = day;
        this.fieldNames = fieldNames;
        this.record = record;
        this.id = id;
    }

    @Override
    public void run() {
        try {
            // capture raw appraiser license data
            Map<String, String> rawData = new TreeMap<>();
            String[] values = record.trim().split("\t");
            for (int i = 0; i < values.length && i < fieldNames.length; i++)
                rawData.put(fieldNames[i], values[i]);
            // create an unidentified License
            License newLicense = new License(rawData);
            // look for an existing license to update
            License oldLicense = Licenses.readLicenseByAscKey(newLicense.getAscKey());
            if (oldLicense != null) {
                // if the license exists, but the raw data changed, then persist a new version
                if (rawData.equals(oldLicense.getRawData()))
                    Imports.incrementIgnored(importId, day);
                else {
                    newLicense.setId(oldLicense.getId());
                    newLicense.setUpdateId(id);
                    Licenses.createLicenseVersion(newLicense);
                    Imports.incrementUpdated(importId, day);
                }
            } else {
                // create a new license
                newLicense.setId(id);
                Licenses.createLicense(newLicense);
                Imports.incrementCreated(importId, day);
            }
        } catch (Exception e) {
            Events.error("LicenseImporter error: " + ExceptionUtils.getRootCauseMessage(e), ExceptionUtils.getStackTrace(e));
        }
    }
}
