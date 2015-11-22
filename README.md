# Real Estate Appraiser Service

## Background

The United States Federal government provides an online, reasonably current (it changes every day) database of all real estate appraisers that are currently certified or licensed by the States and qualified to appraise the value of properties that would serve as collateral for mortgages that would be sold to Freddie Mac or Fannie Mae. It contains ~310k records on ~100k appraisers, and it’s available through a SOAP-based web service (described below).

## Problems

* The service offers only current data; it does not show a history of changes over time, so it cannot reliably be used to determine if an appraiser’s license or certification was active when an appraisal was actually conducted.
* The data set does not uniquely identify individual appraisers that are certified or licensed in multiple states. It’s simply data aggregated from separate sources.
* The service is not highly available. It has both scheduled outages and intermittent unplanned outages.
* The web service is a poorly-designed SOAP-based service. It’s difficult to use and unsuitable for high-speed transactions.

## Objectives

* Build a well-designed, modern RESTful web service that solves the above problems. It will track changes in individual appraiser license status by identifying differences in daily data dumps from the asc.gov web site.
* Open-source the project on GitHub as a demonstration of an excellent web-scale technology stack, including Apache Cassandra and Dropwizard.

## Source Data

The Federal [ASC.gov](https://www.asc.gov/Home.aspx) (Appraisal Subcommittee) web site provides a national registry of all Appraisers with contact information that are currently certified and/or licensed according to state regulations. You can [download](https://www.asc.gov/Content/category1/st_data/v_Export_All.txt) a ~35MB text file (~310K records) every day, or use their web service: [ASC.gov Query Service](https://www.asc.gov/wsvc/ASCQuerySvc.asmx) ([WSDL](https://www.asc.gov/wsvc/ASCQuerySvc.asmx?WSDL)):

* [GetQueryableFields](https://www.asc.gov/wsvc/ASCQuerySvc.asmx?op=GetQueryableFields): St_Abbr, Lic_Number, Lname, Fname, Mname, Name_Suffix, Eff_Date, Exp_Date, Issue_Date, Lic_Type, Status, Company, Phone, Street, City, State, County_Code, Zip, Edi_Capability, Action_Code, Start_Date, End_Date, AQB_Compliant
* [RunQuery](https://www.asc.gov/wsvc/ASCQuerySvc.asmx?op=RunQuery): Experiment with service using REST Console or equivalent.

Header fields in the downloadable tab-delimited text file:

1. **st_abbr**
2. **lic_number**
3. lname
4. fname
5. mname
6. name_suffix
7. street
8. city
9. state
10. zip
11. company
12. phone
13. county
14. county_code
15. status
16. **lic_type**
17. exp_date

The combination of three fields (st_abbr, lic_number, and lic_type) creates a unique index to a record in the file.

# Local Development Environment Setup

To be continued...
