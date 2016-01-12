package info.voxtechnica.appraisers.util;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LicenseStandardizerTest {

    @Test
    public void stateAbbrev() {
        assertThat(LicenseStandardizer.stateAbbrev(null)).isEmpty();
        assertThat(LicenseStandardizer.stateAbbrev("Gu")).isEqualTo("GU");
        assertThat(LicenseStandardizer.stateAbbrev("ms")).isEqualTo("MS");
    }

    @Test
    public void licenseNumber() {
        assertThat(LicenseStandardizer.licenseNumber(null)).isEmpty();
        assertThat(LicenseStandardizer.licenseNumber("ra-778")).isEqualTo("RA-778");
    }

    @Test
    public void licenseType() {
        assertThat(LicenseStandardizer.licenseType(null)).isEmpty();
        assertThat(LicenseStandardizer.licenseType(" ")).isEqualTo("");
    }

    @Test
    public void firstName() {
        assertThat(LicenseStandardizer.firstName(null)).isEmpty();
        assertThat(LicenseStandardizer.firstName("\"S. \"\"John\"\"\"")).isEqualTo("S JOHN");
        assertThat(LicenseStandardizer.firstName("A.  Brook")).isEqualTo("A BROOK");
        assertThat(LicenseStandardizer.firstName("AJ (ANNETTE)")).isEqualTo("AJ ANNETTE");
        assertThat(LicenseStandardizer.firstName("GLENN 'RUSS'")).isEqualTo("GLENN RUSS");
        assertThat(LicenseStandardizer.firstName("D'Anne")).isEqualTo("D'ANNE");
        assertThat(LicenseStandardizer.firstName("E'Tienne")).isEqualTo("E'TIENNE");
    }

    @Test
    public void middleName() {
        assertThat(LicenseStandardizer.middleName(null)).isEmpty();
        assertThat(LicenseStandardizer.middleName("'")).isEmpty();
        assertThat(LicenseStandardizer.middleName("\"")).isEmpty();
        assertThat(LicenseStandardizer.middleName("(")).isEmpty();
        assertThat(LicenseStandardizer.middleName("A.S.")).isEqualTo("A S");
        assertThat(LicenseStandardizer.middleName("  A")).isEqualTo("A");
        assertThat(LicenseStandardizer.middleName("A.  \"FRED\"")).isEqualTo("A FRED");
        assertThat(LicenseStandardizer.middleName("BRENTON   'BRENT\"")).isEqualTo("BRENTON BRENT");
        assertThat(LicenseStandardizer.middleName("D'LANE")).isEqualTo("D'LANE");
        assertThat(LicenseStandardizer.middleName("O'DELL")).isEqualTo("O'DELL");
    }

    @Test
    public void lastName() {
        assertThat(LicenseStandardizer.lastName("ANDERSON, JR.")).isEqualTo("ANDERSON JR");
        assertThat(LicenseStandardizer.lastName("BOSCKIS(WING)")).isEqualTo("BOSCKIS WING");
        assertThat(LicenseStandardizer.lastName("Willis- Buckley")).isEqualTo("WILLIS-BUCKLEY");
        assertThat(LicenseStandardizer.lastName("Bazzell-O'Balle")).isEqualTo("BAZZELL-O'BALLE");
        assertThat(LicenseStandardizer.lastName("D'AGOSTINO")).isEqualTo("D'AGOSTINO");
        assertThat(LicenseStandardizer.lastName("DELANEY O'BRIEN")).isEqualTo("DELANEY O'BRIEN");
        assertThat(LicenseStandardizer.lastName("Gidre'")).isEqualTo("GIDRE");
        assertThat(LicenseStandardizer.lastName("L'Heureux")).isEqualTo("L'HEUREUX");
        assertThat(LicenseStandardizer.lastName("Antonio Legare")).isEqualTo("ANTONIO LEGARE");
    }

    @Test
    public void nameSuffix() {
        assertThat(LicenseStandardizer.nameSuffix(null)).isEmpty();
        assertThat(LicenseStandardizer.nameSuffix("N\\E")).isEmpty();
        assertThat(LicenseStandardizer.nameSuffix(".")).isEmpty();
        assertThat(LicenseStandardizer.nameSuffix("(M)")).isEqualTo("M");
        assertThat(LicenseStandardizer.nameSuffix("Mrs.")).isEqualTo("MRS");
        assertThat(LicenseStandardizer.nameSuffix("Sr.")).isEqualTo("SR");
    }

    @Test
    public void fullName() {
        assertThat(LicenseStandardizer.fullName("JOHN", "", "DOE", "")).isEqualTo("JOHN DOE");
    }

    @Test
    public void telephone() {
        assertThat(LicenseStandardizer.telephone(null)).isEmpty();
        assertThat(LicenseStandardizer.telephone(")--)")).isEmpty();
        assertThat(LicenseStandardizer.telephone("--")).isEmpty();
        assertThat(LicenseStandardizer.telephone("-000-0000")).isEmpty();
        assertThat(LicenseStandardizer.telephone("0")).isEmpty();
        assertThat(LicenseStandardizer.telephone("000-000-0000")).isEmpty();
        assertThat(LicenseStandardizer.telephone("OT -N O-EGON")).isEmpty();
        assertThat(LicenseStandardizer.telephone("___0000000")).isEmpty();
        assertThat(LicenseStandardizer.telephone("URB. PARKVILLE C-15 CALLE HAMILSON")).isEqualTo("15");
        assertThat(LicenseStandardizer.telephone("312-630-9400 x 316")).isEqualTo("312-630-9400-316");
        assertThat(LicenseStandardizer.telephone("@@~@@~9-7822")).isEqualTo("9-7822");
        assertThat(LicenseStandardizer.telephone("9848064")).isEqualTo("9848064");
        assertThat(LicenseStandardizer.telephone("979-378-2273")).isEqualTo("979-378-2273");
        assertThat(LicenseStandardizer.telephone("000-246-5692")).isEqualTo("000-246-5692");
        assertThat(LicenseStandardizer.telephone("(404) 233-0503")).isEqualTo("404-233-0503");
        assertThat(LicenseStandardizer.telephone("(480)595-0188")).isEqualTo("480-595-0188");
    }

    @Test
    public void company() {
        assertThat(LicenseStandardizer.company(null)).isEmpty();
        assertThat(LicenseStandardizer.company("***")).isEmpty();
        assertThat(LicenseStandardizer.company("---")).isEmpty();
        assertThat(LicenseStandardizer.company("===")).isEmpty();
        assertThat(LicenseStandardizer.company(".")).isEmpty();
        assertThat(LicenseStandardizer.company("N/A")).isEmpty();
        assertThat(LicenseStandardizer.company("APPRAISERS\\CONSULTAN")).isEqualTo("APPRAISERS CONSULTAN");
        assertThat(LicenseStandardizer.company("\"AGRIBANK, FCB\"")).isEqualTo("AGRIBANK FCB");
        assertThat(LicenseStandardizer.company("Jean E. Scott, Appraiser")).isEqualTo("JEAN E. SCOTT APPRAISER");
        assertThat(LicenseStandardizer.company("% Tom Wood")).isEqualTo("TOM WOOD");
        assertThat(LicenseStandardizer.company("Ap*praise!")).isEqualTo("APPRAISE");
        assertThat(LicenseStandardizer.company("COMMERCIAL APPRAISAL, INC,.")).isEqualTo("COMMERCIAL APPRAISAL INC");
        assertThat(LicenseStandardizer.company("JACK HARDY               .")).isEqualTo("JACK HARDY");
        assertThat(LicenseStandardizer.company("WELLS FARGO BANK")).isEqualTo("WELLS FARGO BANK");
    }

    @Test
    public void street() {
        assertThat(LicenseStandardizer.street(null)).isEmpty();
        assertThat(LicenseStandardizer.street(".")).isEmpty();
        assertThat(LicenseStandardizer.street("*")).isEmpty();
        assertThat(LicenseStandardizer.street("----")).isEmpty();
        assertThat(LicenseStandardizer.street("% REALCORP          268 N 115TH ST STE 7")).isEqualTo("REALCORP 268 N 115TH ST STE 7");
        assertThat(LicenseStandardizer.street("2035 Commerce Dr # 201")).isEqualTo("2035 COMMERCE DR #201");
    }

    @Test
    public void city() {
        assertThat(LicenseStandardizer.city(null)).isEmpty();
        assertThat(LicenseStandardizer.city(".")).isEmpty();
        assertThat(LicenseStandardizer.city("xx")).isEmpty();
        assertThat(LicenseStandardizer.city("N/L")).isEmpty();
        assertThat(LicenseStandardizer.city("=EWING")).isEqualTo("EWING");
        assertThat(LicenseStandardizer.city("\"RALEIGH, NC  27608")).isEqualTo("RALEIGH, NC 27608");
        assertThat(LicenseStandardizer.city("novato")).isEqualTo("NOVATO");
    }

    @Test
    public void state() {
        assertThat(LicenseStandardizer.state(null)).isEmpty();
        assertThat(LicenseStandardizer.state(".")).isEmpty();
        assertThat(LicenseStandardizer.state("N/")).isEmpty();
        assertThat(LicenseStandardizer.state("N/I")).isEmpty();
        assertThat(LicenseStandardizer.state("Or")).isEqualTo("OR");
        assertThat(LicenseStandardizer.state("0R")).isEqualTo("0R");
    }

    @Test
    public void zipcode() {
        assertThat(LicenseStandardizer.zipcode(null)).isEmpty();
        assertThat(LicenseStandardizer.zipcode("n/l")).isEmpty();
        assertThat(LicenseStandardizer.zipcode("00000-0000")).isEmpty();
        assertThat(LicenseStandardizer.zipcode("980050000")).isEqualTo("980050000");
        assertThat(LicenseStandardizer.zipcode("97914-0000")).isEqualTo("97914-0000");
        assertThat(LicenseStandardizer.zipcode("97528")).isEqualTo("97528");
        assertThat(LicenseStandardizer.zipcode(" 1027-2549")).isEqualTo("1027-2549");
    }

    @Test
    public void countyCode() {
        assertThat(LicenseStandardizer.countyCode(null)).isEmpty();
        assertThat(LicenseStandardizer.countyCode("00")).isEmpty();
        assertThat(LicenseStandardizer.countyCode("Pike")).isEqualTo("231");
        assertThat(LicenseStandardizer.countyCode("7")).isEqualTo("007");
        assertThat(LicenseStandardizer.countyCode("51")).isEqualTo("051");
        assertThat(LicenseStandardizer.countyCode("515")).isEqualTo("515");
    }

    @Test
    public void expirationDate() {
        assertThat(LicenseStandardizer.expirationDate(null)).isEmpty();
        assertThat(LicenseStandardizer.expirationDate(" ")).isEmpty();
        assertThat(LicenseStandardizer.expirationDate("1991-01-01 00:00:00.000")).isEqualTo("1991-01-01");
        assertThat(LicenseStandardizer.expirationDate("2018-03-05 03:00:00.000")).isEqualTo("2018-03-05");
        assertThat(LicenseStandardizer.expirationDate("2020-06-19 00:00:00.000")).isEqualTo("2020-06-19");
    }

}
