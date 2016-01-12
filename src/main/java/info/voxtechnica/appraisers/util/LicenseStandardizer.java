package info.voxtechnica.appraisers.util;

import com.google.common.collect.Lists;

import java.util.List;

public class LicenseStandardizer {

    /**
     * stateAbbrev (State abbreviation) is a 2-character abbreviation of a State in the Union. In the raw data, there
     * are 57 values. Some need to be upper-cased, and some are unusual (GU, MP, PR, VI). There are no blanks.
     */
    public static String stateAbbrev(String state) {
        return state == null ? "" : state.trim().toUpperCase();
    }

    /**
     * licenseNumber is a State-specific (or County-specific?) identifier for the appraiser certification or license.
     * Consequently, the license number patterns are diverse and they are not unique. These data points should be high
     * quality. There are no blanks, but it needs to be upper-cased. It appears that some may be missing hyphens or
     * periods to match the pattern of other very similar license numbers (e.g. X30000001 and X3-0000316).
     */
    public static String licenseNumber(String licenseNumber) {
        // TODO: study the license number patterns by State or County to enhance this standardizer
        return licenseNumber == null ? "" : licenseNumber.trim().toUpperCase();
    }

    /**
     * licenseType is a numeric code (1-6) indicating the type of license or certification. Based on an asc.gov search
     * form, the values correspond to: 1) Licensed, 2) Certified General, 3) Certified Residential, and 4) Transitional
     * License. The value '5' never appears. The value '6' appears only 563 times and its meaning is unknown. There are
     * 6487 blanks.
     */
    public static String licenseType(String licenseType) {
        return licenseType == null ? "" : licenseType.trim();
    }

    /**
     * firstName is the given name of an appraiser. There are 5 blanks and quite a few abbreviations, sometimes using a
     * period and sometimes not. Also, names may appear in single quotes, double quotes, double quotes twice, and
     * parentheses to indicate nick-names. Some names are hypenated. They need to be trimmed, upper-cased, and stripped
     * of periods, quotes, and parentheses.
     */
    public static String firstName(String firstName) {
        return removeStrayCharacters(firstName);
    }

    /**
     * middleName is an additional name for the appraiser. There are 50,584 blanks, and like the lastName, they need to
     * be trimmed, upper-cased, and stripped of extraneous characters. In some cases, the middle name includes name
     * suffixes, such as 'JR' or 'III'.
     */
    public static String middleName(String middleName) {
        return removeStrayCharacters(middleName);
    }

    /**
     * lastName is the family surname of an appraiser. There are no blanks, but the names need to be upper-cased. In
     * some cases, it appears that the same appraiser appears more than once, sometimes with hypenated compound last
     * names and sometimes without. In some cases, the nameSuffix is present in the lastName. There's one stray ".
     */
    public static String lastName(String lastName) {
        return removeStrayCharacters(lastName);
    }

    /**
     * nameSuffix is a modifier of the appraiser's name. It contains common suffix designators, such as 'JR' and other
     * common prefix designators, such as 'MRS'. There are also some unusual values, such as 'N\E' and 'LLC'. Mostly,
     * the field is blank (300,291 records). It needs to be trimmed, upper-cased, and stripped of periods and parens.
     */
    public static String nameSuffix(String suffix) {
        return checkForNoData(removeStrayCharacters(suffix));
    }

    /**
     * fullName is a standardized representation of the appraiser's full name. The following standardized fields are
     * concatenated and space-delimited in this order: firstName, middleName, lastName, nameSuffix.
     */
    public static String fullName(String firstName, String middleName, String lastName, String nameSuffix) {
        return trimWhitespace(String.join(" ", firstName, middleName, lastName, nameSuffix));
    }

    /**
     * telephone is the telephone number of the appraiser. There are 171,744 blanks, and a few records with odd
     * binary data (^B and ^D), '@', and '~'. Some appear to be international (e.g. '011-420-732-227417'). Some have
     * extensions (e.g. '312-630-9400 x 316'). Some are just hypens, underscores, and/or zeros. Some have what may be
     * fragments of street addresses. The data should be stripped of characters that are non-numeric or '-'. Stripped
     * characters should be replaced with spaces, and then the remaining numeric segments should be trimmed and hyphen
     * delimited for standardization. Telephone numbers may be useful for identifying unique appraisers and/or their
     * companies. Google's <a src="https://github.com/googlei18n/libphonenumber">phone number parser</a> may be useful.
     */
    public static String telephone(String telephone) {
        if (telephone == null) return "";
        // strip out invalid telephone number digits
        telephone = telephone.replaceAll("\\D", " ").trim().replaceAll("\\s+", " ");
        // filter out only zeros
        if (!telephone.matches(".*([1-9]).*")) telephone = "";
        // hyphenate phone number segments
        return telephone.replaceAll(" ", "-");
    }

    /**
     * company is the appraiser's company name. Most records are blank (233,231). There are a few large companies
     * (banks), but most are quite small. There are a lot of street addresses, and a few web URLs and email addresses.
     * In some cases, the field is used for notes, such as when a license was suspended and the case number. It needs to
     * be upper-cased, trimmed, and stripped of extraneous characters, such as quotes, '%', '*', and just periods or
     * hypens or '='.
     */
    public static String company(String company) {
        if (company == null) return "";
        // strip extraneous characters
        company = company.toUpperCase().replaceAll("[%,\"!\\\\()=]", " ");
        company = company.replace("*", "");
        // remove obvious "no company" names
        company = checkForNoData(company);
        // remove periods stranded from words
        if (company.matches(".*(\\s+)\\..*")) company = company.replace(".", "");
        // remove spaces before/after hyphens
        company = company.replaceAll("(\\w)(\\s+)(-)", "$1$3").replaceAll("(-)(\\s+)(\\w)", "$1$3");
        // strip out multiple whitespace characters and replace with single spaces
        return trimWhitespace(company);
    }

    /**
     * street is the street address of the appraiser. Data quality is poor, and there are 9549 blanks. In some cases,
     * the field includes company name, web URL, or just a suite number. There are typographical errors (e.g. 'UNTI' for
     * 'UNIT'). There are also special, non-ascii characters in the Spanish alphabet. It needs to be upper-cased and
     * trimmed of extra spaces. Strip percent, asterisk, double quote.
     */
    public static String street(String street) {
        if (street == null) return "";
        street = street.toUpperCase().replaceAll("[%\"\\*]", " ");
        street = street.replaceAll("(#)(\\s+)(\\w+)", "$1$3"); // remove space between # and suite number
        street = checkForNoData(street);
        return trimWhitespace(street);
    }

    /**
     * city is the city of the appraiser. Data quality is poor, and there are 7771 blanks. In some cases the field
     * includes state, state abbreviation, and/or zip code. There are typographical errors (e.g. 'CHESTNUR' instead of
     * 'CHESTNUT'). It needs to be trimmed, upper-cased, and stripped of a few extraneous characters, such as quotes,
     * parentheses, periods, and '='.
     */
    public static String city(String city) {
        if (city == null) return "";
        city = city.toUpperCase().replaceAll("[%\"\\*=()]", " ");
        city = checkForNoData(city);
        return trimWhitespace(city);
    }

    /**
     * state is the state of the appraiser. Data quality is poor; especially compared to stateAbbrev. There are 10,585
     * blanks. Most values are 2-letter abbreviations. Some include zipcode. One is a suite number. It needs to be
     * trimmed, upper-cased, stripped of periods, and nulled if the value is 'N/I' or just 'N/'.
     */
    public static String state(String state) {
        if (state == null) return "";
        state = state.trim().toUpperCase();
        state = checkForNoData(state);
        return trimWhitespace(state);
    }

    /**
     * zipcode is the postal code of the appraiser. There are 8429 blanks. Data quality is patchy; examples include
     * 'Mexico', 'None Indic', 'n/l', and a suite number. Most conform to the US pattern of 5-digit or 10-digit zip
     * codes.
     */
    public static String zipcode(String zipcode) {
        if (zipcode == null) return "";
        if (zipcode.trim().isEmpty() || zipcode.replaceAll("[ -0]", "").isEmpty()) return "";
        zipcode = zipcode.trim().toUpperCase();
        zipcode = checkForNoData(zipcode);
        return trimWhitespace(zipcode);
    }

    /**
     * address is a standardized representation of the postal address of the appraiser (or their company?). The following
     * standardized fields are concatenated and space-delimited in this order: street, city, state, zipcode. It's an
     * attempt to normalize poor-quality data in the wrong fields and make address look-ups simpler. Addresses may be
     * useful for identifying unique appraisers and/or their companies. We will probably also need company in the address
     * field for address standardization and geolocation.
     */
    public static String address(String street, String city, String state, String zipcode) {
        return trimWhitespace(String.join(" ", street, city, state, zipcode));
    }

    /**
     * county is the County name for the appraiser license. Data quality appears to be good (all upper-case names), but
     * there are 142,917 blanks (about half the data).
     */
    public static String county(String county) {
        if (county == null) return "";
        return trimWhitespace(county.toUpperCase());
    }

    /**
     * countyCode is probably the 3-digit <a href="https://www.census.gov/geo/reference/codes/cou.html">FIPS county code</a>.
     * It does not include the 2-digit FIPS State code. There are 139,549 blanks (less than the field 'county'). Most
     * values are 3-digit numbers, some including leading zeros. And then there's 'Pike'. A code of '00' or '000' is
     * invalid. There is no county code '000' or '999', but both appear in the data.
     */
    public static String countyCode(String countyCode) {
        if (countyCode == null) return "";
        if ("Pike".equals(countyCode)) countyCode = "231"; // GA,13,231,Pike County,H1
        countyCode = countyCode.replaceAll("\\D", " ").trim();
        if (countyCode.isEmpty() || countyCode.replaceAll("0", "").isEmpty()) return "";
        return String.format("%03d", Integer.valueOf(countyCode)); // ensure leading zeros
    }

    /**
     * status is the license status, either 'A' (active) or 'I' (inactive). There are 92,833 active, 211,150 inactive,
     * and 6,487 blank records.
     */
    public static String status(String status) {
        return status == null ? "" : status.trim().toUpperCase();
    }

    /**
     * expirationDate is a local date for when the license expires. It has a fixed format (e.g. 1991-01-01 00:00:00.000).
     * There are 6,805 blanks, and we can ignore the timestamp. Virtually all are zeros, except for a few references to
     * 2, 3, or 4 am. Years range from 1991 to 2020.
     */
    public static String expirationDate(String expirationDate) {
        if (expirationDate == null || expirationDate.trim().isEmpty()) return "";
        return expirationDate.substring(0, 10);
    }

    /**
     * Standardizes a field by uppercasing and removing stray characters (quotes, parens, periods, commas, etc.)
     *
     * @param field String to standardize
     * @return space-delimited words in a string
     */
    private static String removeStrayCharacters(String field) {
        if (field == null) return "";
        field = field.toUpperCase().replaceAll("[\\.,\"()]", " ");
        // strip out single quotes, but leave meaningful apostrophes (e.g. O'NEIL)
        if (field.contains("'") && !field.matches(".*(\\w)(')(\\w).*"))
            field = field.replace("'", " ");
        // remove spaces before/after hyphens
        field = field.replaceAll("(\\w)(\\s+)(-)", "$1$3").replaceAll("(-)(\\s+)(\\w)", "$1$3");
        // strip out multiple whitespace characters and replace with single spaces
        return trimWhitespace(field);
    }

    /**
     * Replace blocks of whitespace with single spaces
     *
     * @param field String to standardize
     * @return space-delimited words in a string
     */
    private static String trimWhitespace(String field) {
        return field.trim().replaceAll("\\s+", " ");
    }

    /**
     * Check a string to see if the contents indicate that no data were available, and truncate if so
     *
     * @param field String to standardize
     * @return empty string if no data were available; otherwise return the unmodified string
     */
    private static String checkForNoData(String field) {
        if (noData.contains(field)) return "";
        if (field.replace("-", "").equals("")) return "";
        if (field.replace("*", "").equals("")) return "";
        if (field.replace("X", "").equals("")) return "";
        return field;
    }

    /**
     * These strings appear to indicate that no data were available
     */
    private static List<String> noData = Lists.newArrayList(".", "NI", "N/", "N/A", "N/I", "N/L", "N\\E", "NONE", "NONE INDIC", "NONE INDICATED", "NOT I", "NOT INDICA", "UNEMPLOYED");

    /**
     * These nameSuffixes appear in the data and are considered valid
     */
    public static List<String> nameSuffixes = Lists.newArrayList("JR", "SR", "I", "II", "III", "IV", "V", "VI", "PA", "DR", "MR", "MS", "MRS", "LLC");
}
