package info.voxtechnica.appraisers.model;

import lombok.Data;
import org.apache.commons.lang3.ObjectUtils;

import java.util.Comparator;
import java.util.Map;

/**
 * Licenses are used to capture and retain raw, unimproved information about appraisers, their contact information, and
 * their licenses and certifications. It’s also used to store standardized (e.g. uppercased) data. It is used to track
 * changes in the data set, and it will only be recorded for original and modified records in the daily asc.gov batches.
 * The three attributes stateAbbrev, licenseNumber, and licenseType provide a unique index into the asc.gov data.
 * Unmodified raw strings are stored in rawData. Standardized, enhanced data are stored in the named fields.
 * Observations of the raw data on October 18, 2015 are provided in the field descriptions below.
 */
@Data
public class License implements Comparable<License> {
    /**
     * id is the unique identifier of the License in the Appraiser Service. It's a TUID, and it never changes.
     */
    private String id;

    public String getCreatedAt() {
        return id == null ? null : (new Tuid(id)).getCreatedAt();
    }

    /**
     * updateId identifies a specific version of the License. It's another TUID with an embedded millisecond timestamp.
     */
    private String updateId;

    public String getUpdatedAt() {
        return updateId == null ? null : (new Tuid(updateId)).getCreatedAt();
    }

    /**
     * ascKey is an apparent unique index into the ASC license table. It is a concatenated string of upper-cased raw
     * values for fields: st_abbr + lic_number + lic_type. No standardization other than upper-casing is done on this
     * key. Empty strings are used in place of nulls.
     */
    private String ascKey;

    /**
     * stateAbbrev (State abbreviation) is a 2-character abbreviation of a State in the Union. In the raw data, there
     * are 57 values. Some need to be upper-cased, and some are unusual (GU, MP, PR, VI). There are no blanks.
     */
    private String stateAbbrev;

    /**
     * licenseNumber is a State-specific (or County-specific?) identifier for the appraiser certification or license.
     * Consequently, the license number patterns are diverse and they are not unique. These data points should be high
     * quality. There are no blanks, but it needs to be upper-cased. It appears that some may be missing hyphens to
     * match the pattern of other very similar license numbers (e.g. X30000001 and X3-0000316).
     */
    private String licenseNumber;

    /**
     * licenseType is a numeric code (1-6) indicating the type of license or certification. Based on an asc.gov search
     * form, the values correspond to: 1) Licensed, 2) Certified General, 3) Certified Residential, and 4) Transitional
     * License. The value '5' never appears. The value '6' appears only 563 times and its meaning is unknown. There are
     * 6487 blanks.
     */
    private String licenseType;

    public String getLicenseTypeDisplay() {
        switch (licenseType) {
            case "3":
                return "Certified Residential";
            case "2":
                return "Certified General";
            case "1":
                return "Licensed";
            case "4":
                return "Transitional License";
            default:
                return "Unspecified";
        }
    }

    /**
     * fullName is a standardized representation of the appraiser's full name. The following standardized fields are
     * concatenated and space-delimited in this order: firstName, middleName, lastName, nameSuffix.
     */
    private String fullName;

    /**
     * lastName is the family surname of an appraiser. There are no blanks, but the names need to be upper-cased. In
     * some cases, it appears that the same appraiser appears more than once, sometimes with hypenated compound last
     * names and sometimes without. In some cases, the nameSuffix is present in the lastName. There's one stray ".
     */
    private String lastName;

    /**
     * firstName is the given name of an appraiser. There are 5 blanks and quite a few abbreviations, sometimes using a
     * period and sometimes not. Also, names may appear in single quotes, double quotes, double quotes twice, and
     * parentheses to indicate nick-names. Some names are hypenated. They need to be trimmed, upper-cased, and stripped
     * of periods, quotes, and parentheses.
     */
    private String firstName;

    /**
     * middleName is an additional name for the appraiser. There are 50,584 blanks, and like the lastName, they need to
     * be trimmed, upper-cased, and stripped of extraneous characters. In some cases, the middle name includes name
     * suffixes, such as 'JR' or 'III'.
     */
    private String middleName;

    /**
     * nameSuffix is a modifier of the appraiser's name. It contains common suffix designators, such as 'JR' and other
     * common prefix designators, such as 'MRS'. There are also some unusual values, such as 'N\E' and 'LLC'. Mostly,
     * the field is blank (300,291 records). It needs to be trimmed, upper-cased, and stripped of periods and parens.
     */
    private String nameSuffix;

    /**
     * telephone is the telephone number of the appraiser. There are 171,744 blanks, and a few records with odd
     * binary data (^B and ^D), '@', and '~'. Some appear to be international (e.g. '011-420-732-227417'). Some are just
     * hypens, underscores, and/or zeros. Some have what may be fragments of street addresses. The data should be
     * stripped of characters that are non-numeric or '-'. Stripped characters should be replaced with spaces, and then
     * the remaining numeric segments should be trimmed and hyphen-delimited for standardization. Telephone numbers may
     * be useful for identifying unique appraisers and/or their companies.
     */
    private String telephone;

    /**
     * address is a standardized representation of the postal address of the appraiser (or their company?). The following
     * standardized fields are concatenated and space-delimited in this order: street, city, state, zipcode. It's an
     * attempt to normalize poor-quality data in the wrong fields and make address look-ups simpler. Addresses may be
     * useful for identifying unique appraisers and/or their companies.
     */
    private String address;

    /**
     * street is the street address of the appraiser. Data quality is poor, and there are 9549 blanks. In some cases,
     * the field includes company name, web URL, or just a suite number. There are typographical errors (e.g. 'UNTI' for
     * 'UNIT'). There are also special, non-ascii characters in the Spanish alphabet. It needs to be upper-cased and
     * trimmed of extra spaces.
     */
    private String street;

    /**
     * city is the city of the appraiser. Data quality is poor, and there are 7771 blanks. In some cases the field
     * includes state, state abbreviation, and/or zip code. There are typographical errors (e.g. 'CHESTNUR' instead of
     * 'CHESTNUT'). It needs to be trimmed, upper-cased, and stripped of a few extraneous characters, such as quotes,
     * parentheses, periods, and '='.
     */
    private String city;

    /**
     * state is the state of the appraiser. Data quality is poor; especially compared to stateAbbrev. There are 10,585
     * blanks. Most values are 2-letter abbreviations. Some include zipcode. One is a suite number. It needs to be
     * trimmed, upper-cased, stripped of periods, and nulled if the value is 'N/I' or just 'N/'.
     */
    private String state;

    /**
     * zipcode is the postal code of the appraiser. There are 8429 blanks. Data quality is patchy; examples include
     * 'Mexico', 'None Indic', 'n/l', and a suite number. Most conform to the US pattern of 5-digit or 10-digit zip
     * codes.
     */
    private String zipcode;

    /**
     * company is the appraiser's company name. Most records are blank (233,231). There are a few large companies
     * (banks), but most are quite small. There are a lot of street addresses, and a few web URLs and email addresses.
     * In some cases, the field is used for notes, such as when a license was suspended and the case number. It needs to
     * be upper-cased, trimmed, and stripped of extraneous characters, such as quotes, '%', '*', and just periods or
     * hypens or '='.
     */
    private String company;

    /**
     * county is the County name for the appraiser license. Data quality appears to be good (all upper-case names), but
     * there are 142,917 blanks (about half the data).
     */
    private String county;

    /**
     * countyCode is probably the 3-digit <a href="https://www.census.gov/geo/reference/codes/cou.html">FIPS county code</a>.
     * It does not include the 2-digit FIPS State code. There are 139,549 blanks (less than the field 'county'). Most
     * values are 3-digit numbers, some including leading zeros. And then there's 'Pike'.
     */
    private String countyCode;

    /**
     * status is the license status, either 'A' (active) or 'I' (inactive). There are 92,833 active, 211,150 inactive,
     * and 6,487 blank records.
     */
    private String status;

    public String getStatusDisplay() {
        switch (status) {
            case "A":
                return "Active";
            case "I":
                return "Inactive";
            default:
                return "Unspecified";
        }
    }

    /**
     * expirationDate is a local date for when the license expires. It has a fixed format (e.g. 1991-01-01 00:00:00.000).
     * There are 6,805 blanks, and we can ignore the timestamp. Virtually all are zeros, except for a few references to
     * 2, 3, or 4 am. Years range from 1991 to 2020.
     */
    private String expirationDate;

    /**
     * rawData contains a map of original, unmodified data values. The first line of a daily tab-delimited data dump
     * includes column headers. These values are used as keys in the map, and the exact corresponding strings found in
     * the data set are used as values. These data are used to detect changes in appraiser license records and as raw
     * material for standardized fields.
     */
    private Map<String, String> rawData;

    /**
     * Default Constructor
     */
    public License() {
    }

    /**
     * Constructor: create an unidentified (no id or updateId) License from a rawData map. Standardize the data as
     * appropriate.
     *
     * @param rawData Map of raw data downloaded from asc.gov. Keys are asc.gov field names. Values are unmodified data.
     */
    public License(Map<String, String> rawData) {
        this.rawData = rawData;
        // TODO: carefully standardize the data
        stateAbbrev = rawData.containsKey("st_abbr") ? rawData.get("st_abbr").trim().toUpperCase() : "";
        licenseNumber = rawData.containsKey("lic_number") ? rawData.get("lic_number").trim().toUpperCase() : "";
        licenseType = rawData.containsKey("lic_type") ? rawData.get("lic_type").trim() : "";
        ascKey = stateAbbrev + licenseNumber + licenseType;

        lastName = rawData.containsKey("lname") ? rawData.get("lname").trim().toUpperCase() : "";
        firstName = rawData.containsKey("fname") ? rawData.get("fname").trim().toUpperCase() : "";
        middleName = rawData.containsKey("mname") ? rawData.get("mname").trim().toUpperCase() : "";
        nameSuffix = rawData.containsKey("name_suffix") ? rawData.get("name_suffix").trim().toUpperCase() : "";
        fullName = String.join(" ", firstName, middleName, lastName, nameSuffix);

        company = rawData.containsKey("company") ? rawData.get("company").trim().toUpperCase() : "";
        telephone = rawData.containsKey("phone") ? rawData.get("phone").trim() : "";

        street = rawData.containsKey("street") ? rawData.get("street").trim().toUpperCase() : "";
        city = rawData.containsKey("city") ? rawData.get("city").trim().toUpperCase() : "";
        state = rawData.containsKey("state") ? rawData.get("state").trim().toUpperCase() : "";
        zipcode = rawData.containsKey("zip") ? rawData.get("zip").trim().toUpperCase() : "";
        address = String.join(" ", street, city, state, zipcode);

        county = rawData.containsKey("county") ? rawData.get("county").trim().toUpperCase() : "";
        countyCode = rawData.containsKey("county_code") ? rawData.get("county_code").trim().toUpperCase() : "";

        status = rawData.containsKey("status") ? rawData.get("status").trim().toUpperCase() : "";
        expirationDate = rawData.containsKey("exp_date") ? rawData.get("exp_date").trim() : "";
        expirationDate = expirationDate.length() > 10 ? expirationDate.substring(0, 10) : expirationDate;
    }

    @Override
    public int compareTo(License that) {
        return Chronological.compare(this, that);
    }

    public static Comparator<License> Chronological = new Comparator<License>() {
        @Override
        public int compare(License one, License two) {
            if (one == null && two == null) return 0;
            int c0 = one == null ? -1 : (two == null ? 1 : 0);
            if (c0 != 0) return c0;
            int c1 = ObjectUtils.compare(one.getId(), two.getId());
            if (c1 != 0) return c1;
            return ObjectUtils.compare(one.getUpdateId(), two.getUpdateId());
        }
    };

    public static Comparator<License> ReverseChronological = new Comparator<License>() {
        @Override
        public int compare(License one, License two) {
            return Chronological.compare(two, one);
        }
    };
}
