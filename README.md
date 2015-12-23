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

# Development Environment Setup

Unless otherwise indicated, install the most current stable version of the various software packages highlighted below. The details for this vary by operating system. If you are running OS X, then [Homebrew](http://brew.sh)'s `brew install` is as simple as `apt-get install` on Linux. Highly recommended.

**Steps**:

1. Install [git](http://git-scm.com/), set up your [SSH key](https://help.github.com/articles/generating-ssh-keys/), and fork and/or clone this repository
2. Install [Oracle Java Development Kit (JDK) SE 8](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
3. Install an IDE (e.g. [Intellij IDEA](https://www.jetbrains.com/idea/), [Eclipse](http://www.eclipse.org/downloads/), or [NetBeans](https://netbeans.org/))
4. Install [Maven](http://maven.apache.org/download.cgi) (optional; it may be included with your IDE)
5. Install [Cassandra](http://planetcassandra.org/cassandra/) (optional; it's embedded in the application)
6. Install a browser-based REST API client (e.g. [Postman](https://www.getpostman.com/))
7. Build the application using Maven
8. Run the application using the server command
9. Bootstrap a new user account

## Build the Application

Build the application using Maven, from the project's root folder:

```
mvn clean package
```

## Run the Application

To run the application, you only need the executable jar file and a configuration file. A sample configuration file is provided. Copy this file to the project's root folder and update it as desired. If you enable [SendGrid](https://sendgrid.com/) (email) or [Slack](https://slack.com/) (messaging) support, then you'll need to supply your own account credentials.

```
cp src/main/resources/config/appraisers-example.yaml ./appraisers.yaml
```

Execute the following command (substituting the correct version number) to run the application. Note the command 'server' and the yaml configuration file.

```
java -jar target/appraisers-1.0-SNAPSHOT.jar server appraisers.yaml
```

By default, the API ([documentation](http://localhost:8080/docs)) is available on port 8080, and administrative functions (e.g. [healthcheck](http://localhost:8081/healthcheck), [metrics](http://localhost:8081/metrics)) are available on port 8081. To stop the service, simply press <Ctrl+C>.

If, as in the example configuration file, you've set the Cassandra host name to 'embedded', the application will run the embedded Cassandara database server. It will create a ./cassandra folder for the configuration and data files in your current directory, and it will be there for you the next time you run the application. If you want to get rid of old test data, simply delete the folder and you can start fresh again. If you've installed your own instance of Cassandra, simply update the host name in the configuration file accordingly. The application will instantiate its schema into the database if it doesn't already exist.

## Bootstrap a New User Account

You can bootstrap a new user account using a three-step process:

1. **Create a Reset Token**: using a tool like Postman, POST empty JSON to a URI that includes your email address. This will create the account if it doesn’t already exist. The application will then send a password reset token to the email address.
2. **Enter a new Password**: use the token to PUT a new password to a URI that includes both the email address and the token supplied. You can now use your email address and password with Basic Authentication to use the API. Or, if you prefer, you can create a bearer token (step 3).
3. **Create an OAuth2 Bearer Token**: once you have proper username and password credentials, you can either log in using Basic Authentication or create an OAuth bearer token to access the API.

These steps are detailed below.

### Create a Reset Token

POST empty JSON {} to this URI, substituting your own email address:

```
POST http://localhost:8080/v1/users/username@example.com/resets
Content-Type: application/json
Accept: application/json
Body:
{}
Response: 200 OK
{ "status": "SENT" }
```

If the status is NOT_SENT, then that means that either email support (SendGrid) is disabled, or your email address domain name is not included in the list of approved domains. In this case, you can use the admin user's bearer token (found in the configuration file) to get the reset token directly from the database. Substitute your email address in the following example:

```
GET http://localhost:8080/v1/users/username@example.com
Accept: application/json
Authorization: Bearer 163c88b0-8c01-11e5-bb53-dd09e7fbdd1b
Response: 200 OK
{
    "id": "2W0NTHMZ6FR7",
    "updateId": "2W0NUN7KXPVN",
    "email": "username@example.com",
    "passwordHash": "1020b9a5af92aaa016578b5b8d83952c290c152c5bc14ee391ed7fcf9ee646ef",
    "resetToken": "2W0NUN7KXPOJ",
    "status": "PENDING",
    "name": "2W0NTHMZ6FR7",
    "domain": "example.com",
    "createdAt": "2015-12-21T20:30:45.057-08:00",
    "updatedAt": "2015-12-21T20:36:30.264-08:00"
}
```

The resetToken value (e.g. 2W0NUN7KXPOJ above) is the token that would have been emailed.

### Enter a new Password

Using your email address and the password reset token, create a new password for your account with a PUT to a URI of the form v1/users/{email}/resets/{token}. For example:

```
PUT http://localhost:8080/v1/users/username@example.com/resets/2W0NUN7KXPOJ
Content-Type: application/json
Accept: application/json
Body:
{ "password": "myNewPassword" }
Response: 200 OK
{ "status": "RESET" }
```

Now you can use your email address and password to authenticate your API access with a Basic Authorization header.

### Create an OAuth2 Bearer Token

A preferred way of authenticating your access is with an OAuth2 bearer token. You can create such a token by posting your email address and password to v1/tokens like so:

```
POST http://localhost:8080/v1/tokens
Content-Type: application/json
Accept: application/json
Body:
{
    "username":"username@example.com",
    "password":"mySecretPassword"
}
Response: 201 Created
{
    "token_type": "bearer",
    "access_token": "e66edde0-a6be-11e4-879f-0335bca1f646"
}
```

You can have more than one bearer token in use for a single user account (e.g. different tokens for different devices). To deliberately deactivate a particular token, you can delete it like so:
```
DELETE http://localhost:8080/v1/tokens/e66edde0-a6be-11e4-879f-0335bca1f646
Authorization: Bearer e66edde0-a6be-11e4-879f-0335bca1f646
Response: 204 No Content
```

## Load ASC.gov Data

The application has two importers for loading data from ASC.gov:

1. **ImportLicenseFileTask**: import a specific file downloaded previously; and
2. **ImportLicensesTask**: download and import data directly from ASC.gov.

The simplest way to get started is to use option 2, ImportLicensesTask. To invoke it, execute the following command:

```
curl -X POST http://localhost:8081/tasks/import-licenses
```

This will trigger a download, and the concurrent importers will go to work immediately. You can configure the number of parallel workers in appraisers.yaml (the threadPool section). On a reasonably fast computer, it will take about 1 minute to import more than 300,000 appraiser licenses.

If you put this task in a cron job, you'll get regular updates to your dataset. Only new or modified data are imported. After the initial import, most of the records are ignored. To check on how your regular imports are doing, you can use the following API query. Leave off the 'day' query parameter to get the complete history.

```
GET http://localhost:8080/v1/imports?day=20151021
Accept: application/json
Authorization: Bearer 163c88b0-8c01-11e5-bb53-dd09e7fbdd1b
Response: 200 OK
[
    {
        "id": "2W0FV8515I5D",
        "day": 20151021,
        "created": 22,
        "updated": 361,
        "ignored": 310119,
        "total": 310502,
        "createdAt": "2015-12-20T20:44:30.613-08:00"
    }
]
```

## Browse Appraiser Licenses

Once you've got data in the database, you can query for appraisers like this:

```
GET http://localhost:8080/v1/licenses?state=AZ&license_number=20043
Accept: application/json
Authorization: Bearer 163c88b0-8c01-11e5-bb53-dd09e7fbdd1b
Response: 200 OK
[
    {
        "id": "2VM6ST0LS2E9",
        "updateId": "2VMUVVRQRME9",
        "ascKey": "AZ200433",
        "stateAbbrev": "AZ",
        "licenseNumber": "20043",
        "licenseType": "3",
        "fullName": "MICHAEL F CUMMER ",
        "lastName": "CUMMER",
        "firstName": "MICHAEL",
        "middleName": "F",
        "nameSuffix": "",
        "telephone": "602-910-0688",
        "address": "257 N. 104TH PLACE APACHE JUNCTION AZ 85120",
        "street": "257 N. 104TH PLACE",
        "city": "APACHE JUNCTION",
        "state": "AZ",
        "zipcode": "85120",
        "company": "",
        "county": "MARICOPA",
        "countyCode": "013",
        "status": "A",
        "expirationDate": "2016-08-31",
        "rawData": {
            "city": "APACHE JUNCTION",
            "company": "",
            "county": "MARICOPA",
            "county_code": "013",
            "exp_date": "2016-08-31 00:00:00.000",
            "fname": "MICHAEL",
            "lic_number": "20043",
            "lic_type": "3",
            "lname": "CUMMER",
            "mname": "F",
            "name_suffix": "",
            "phone": "602-910-0688",
            "st_abbr": "AZ",
            "state": "AZ",
            "status": "A",
            "street": "257 N. 104TH PLACE",
            "zip": "85120"
        },
        "licenseTypeDisplay": "Certified Residential",
        "statusDisplay": "Active",
        "createdAt": "2015-10-18T00:00:05.196-07:00",
        "updatedAt": "2015-10-21T00:00:05.196-07:00"
    }
]
```

There are a number of different query parameters you can use for finding licenses, and the specifics are included in the detailed [API documentation](https://appraisers.voxtechnica.info/docs#!/licenses/readLicenses_get_4).

Note that the original, unmodified data downloaded from ASC.gov are included in the 'rawData' field. The other attributes in the license record are lightly standardized (e.g. uppercasing text) in the interest of improving data quality and searchability.

To see the complete revision/update history for a particular license, you can use the license ID like the following:

```
GET http://localhost:8080/v1/licenses/2VM6ST0LS2E9/versions
Accept: application/json
Authorization: Bearer 163c88b0-8c01-11e5-bb53-dd09e7fbdd1b
Response: 200 OK
[
    {
    ... omitted for brevity ...
    }
]
```

You'll get an array of license records, all of which are chronological updates to the same license.
