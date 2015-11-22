package info.voxtechnica.appraisers.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.dropwizard.Configuration;
import io.dropwizard.client.HttpClientConfiguration;

/**
 * Configuration settings for the Appraiser Service application
 */
public class ApplicationConfiguration extends Configuration {

    /**
     * The static configuration field makes this application configuration available globally as a static singleton.
     * It must be initialized following instantiation in MainApplication.run()
     */
    private static ApplicationConfiguration configuration = null;

    @JsonIgnore
    public static void set(ApplicationConfiguration config) {
        configuration = config;
    }

    @JsonIgnore
    public static ApplicationConfiguration get() {
        return configuration;
    }

    @JsonIgnore
    public static Boolean isInitialized() {
        return configuration != null;
    }

    /**
     * Application Version Number, from the Jar file if available. If running in a debugger, then use "0000"
     */
    private String version = getClass().getPackage().getImplementationVersion();

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version == null ? "0000" : version;
    }

    /**
     * External base URL for the API (e.g. https://api.voxtechnica.info)
     */
    private String apiBaseUrl;

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    /**
     * External base URL for the Web Application (e.g. https://www.voxtechnica.info)
     */
    private String webBaseUrl;

    public String getWebBaseUrl() {
        return webBaseUrl;
    }

    public void setWebBaseUrl(String webBaseUrl) {
        this.webBaseUrl = webBaseUrl;
    }

    /**
     * Network Interface (e.g. "eth0" on Linux or "en0" on a Mac) for IP Address to use for an auto-assigned TuidFactory
     * Node ID. Extremely important: the IP Address must be unique to ensure cluster-unique identifiers! Using localhost
     * is especially dangerous, because it exists on every machine. If you're working offline, though, and developing
     * locally, localhost is all you've got. Use 'auto' for the first available non-local address. Use 'any' to also
     * allow localhost (dangerous).
     */
    private String networkInterface = "auto";

    public String getNetworkInterface() {
        return networkInterface;
    }

    public void setNetworkInterface(String networkInterface) {
        this.networkInterface = networkInterface;
    }

    /**
     * Security Realm configuration for Basic Authentication
     */
    private RealmConfiguration realm = new RealmConfiguration();

    public RealmConfiguration getRealm() {
        return realm;
    }

    public void setRealm(RealmConfiguration realm) {
        this.realm = realm;
    }

    /**
     * Apache Cassandra database configuration
     */
    private CassandraConfiguration cassandra = new CassandraConfiguration();

    public CassandraConfiguration getCassandra() {
        return cassandra;
    }

    public void setCassandra(CassandraConfiguration cassandra) {
        this.cassandra = cassandra;
    }

    /**
     * SendGrid email service configuration
     */
    private final SendGridConfiguration sendgrid = new SendGridConfiguration();

    public SendGridConfiguration getSendgrid() {
        return sendgrid;
    }

    /**
     * Slack messaging service configuration
     */
    private SlackConfiguration slack = new SlackConfiguration();

    public SlackConfiguration getSlack() {
        return slack;
    }

    public void setSlack(SlackConfiguration slack) {
        this.slack = slack;
    }

    /**
     * Event Service configuration
     */
    private EventConfiguration event = new EventConfiguration();

    public EventConfiguration getEvent() {
        return event;
    }

    public void setEvent(EventConfiguration event) {
        this.event = event;
    }

    /**
     * External web services Client configuration (used by multiple clients, including SendGridClient and SlackClient)
     */
    private HttpClientConfiguration httpClient = new HttpClientConfiguration();

    public HttpClientConfiguration getHttpClient() {
        return httpClient;
    }

    public void setHttpClient(HttpClientConfiguration httpClient) {
        this.httpClient = httpClient;
    }

// TODO: replace the above HttpClientConfiguration with a JerseyClientConfiguration
//    private JerseyClientConfiguration httpClient = new JerseyClientConfiguration();
//
//    public JerseyClientConfiguration getHttpClient() {
//        return httpClient;
//    }
//
//    public void setHttpClient(JerseyClientConfiguration httpClient) {
//        this.httpClient = httpClient;
//    }

}
