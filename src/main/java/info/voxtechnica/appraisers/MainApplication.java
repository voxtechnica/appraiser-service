package info.voxtechnica.appraisers;

import com.datastax.driver.core.Session;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import com.wordnik.swagger.config.ConfigFactory;
import com.wordnik.swagger.config.ScannerFactory;
import com.wordnik.swagger.config.SwaggerConfig;
import com.wordnik.swagger.jaxrs.config.DefaultJaxrsScanner;
import com.wordnik.swagger.jaxrs.listing.ApiDeclarationProvider;
import com.wordnik.swagger.jaxrs.listing.ApiListingResourceJSON;
import com.wordnik.swagger.jaxrs.listing.ResourceListingProvider;
import com.wordnik.swagger.jaxrs.reader.DefaultJaxrsApiReader;
import com.wordnik.swagger.reader.ClassReaders;
import info.voxtechnica.appraisers.auth.ApiUserAuthorizer;
import info.voxtechnica.appraisers.auth.ApiUserBasicAuthenticator;
import info.voxtechnica.appraisers.auth.ApiUserTokenAuthenticator;
import info.voxtechnica.appraisers.client.CassandraClient;
import info.voxtechnica.appraisers.client.SendGridClient;
import info.voxtechnica.appraisers.client.SlackClient;
import info.voxtechnica.appraisers.command.SchemaCommand;
import info.voxtechnica.appraisers.config.ApplicationConfiguration;
import info.voxtechnica.appraisers.config.RealmConfiguration;
import info.voxtechnica.appraisers.config.ThreadPoolConfiguration;
import info.voxtechnica.appraisers.db.CassandraMetricSet;
import info.voxtechnica.appraisers.db.dao.*;
import info.voxtechnica.appraisers.health.*;
import info.voxtechnica.appraisers.model.Metric;
import info.voxtechnica.appraisers.model.Node;
import info.voxtechnica.appraisers.model.User;
import info.voxtechnica.appraisers.resource.*;
import info.voxtechnica.appraisers.service.LicenseService;
import info.voxtechnica.appraisers.task.ImportLicenseFileTask;
import info.voxtechnica.appraisers.task.ImportUsersTask;
import info.voxtechnica.appraisers.util.JsonSerializer;
import info.voxtechnica.appraisers.util.NetworkUtils;
import info.voxtechnica.appraisers.util.TuidFactory;
import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.auth.AuthDynamicFeature;
import io.dropwizard.auth.AuthFilter;
import io.dropwizard.auth.AuthValueFactoryProvider;
import io.dropwizard.auth.CachingAuthenticator;
import io.dropwizard.auth.basic.BasicCredentialAuthFilter;
import io.dropwizard.auth.basic.BasicCredentials;
import io.dropwizard.auth.chained.ChainedAuthFilter;
import io.dropwizard.auth.oauth.OAuthCredentialAuthFilter;
import io.dropwizard.client.HttpClientBuilder;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.forms.MultiPartBundle;
import io.dropwizard.lifecycle.setup.ExecutorServiceBuilder;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.views.ViewBundle;
import org.apache.http.client.HttpClient;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.ws.rs.client.Client;
import java.io.IOException;
import java.net.InetAddress;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

public class MainApplication extends Application<ApplicationConfiguration> {
    private static final Logger LOG = LoggerFactory.getLogger(MainApplication.class);

    public static void main(final String[] args) throws Exception {
        new MainApplication().run(args);
    }

    @Override
    public String getName() {
        return "appraisers";
    }

    /**
     * Bootstrap the service (add Bundles, Commands, or Jackson modules) before parsing a configuration file, providing
     * a command-line interface, or running as a server.
     */
    @Override
    public void initialize(final Bootstrap<ApplicationConfiguration> bootstrap) {
        bootstrap.addCommand(new SchemaCommand());
        bootstrap.addBundle(new MultiPartBundle());
        bootstrap.addBundle(new ViewBundle<>());
        bootstrap.addBundle(new AssetsBundle("/assets/swagger", "/swagger", null, "swagger"));
        bootstrap.addBundle(new AssetsBundle("/assets/", "/assets", "index.html", "assets"));
    }

    /**
     * Environment consists of all the Resources, servlets, filters, Health Checks, Jersey providers, Managed Objects,
     * Tasks, and Jersey properties your service provides. Keep the run() method clean. If creating an instance of
     * something is complicated, extract that logic into a factory.
     */
    @Override
    @SuppressWarnings("unchecked")
    public void run(final ApplicationConfiguration configuration, final Environment environment) throws Exception {
        long startAppraiserService = System.currentTimeMillis();

        // Initialize the ApplicationConfiguration to make settings globally accessible
        ApplicationConfiguration.set(configuration);
        environment.healthChecks().register("version", new ApplicationHealthCheck());

        // Connect to Cassandra and register its health check and performance metrics
        CassandraClient cassandraClient = new CassandraClient(configuration.getCassandra());
        environment.lifecycle().manage(cassandraClient); // managed for graceful shutdown
        environment.healthChecks().register("cassandra", new CassandraHealthCheck(cassandraClient));
        environment.metrics().registerAll(new CassandraMetricSet(cassandraClient.getCluster()));
        Session dbSession = cassandraClient.getSession();
        if (dbSession == null) {
            LOG.error("Error connecting to Cassandra keyspace {}. Does it exist?", cassandraClient.getKeyspaceName());
            System.exit(1);
        }

        // Fetch an auto-assigned TuidFactory Node ID from Cassandra
        long startTime = System.currentTimeMillis();
        Nodes.initialize(cassandraClient);
        InetAddress ipAddress = NetworkUtils.getIpAddress(configuration.getNetworkInterface());
        if (ipAddress == null) {
            LOG.error("Unable to identify IP Address for Network Interface {}. Available:", configuration.getNetworkInterface());
            Map<String, InetAddress> addresses = NetworkUtils.getInterfaceAddresses(false);
            for (String networkInterface : addresses.keySet())
                LOG.info("Available: interface {} address {}", networkInterface, addresses.get(networkInterface));
            System.exit(1);
        }
        Node node = Nodes.getNode(ipAddress);
        if (node == null) {
            LOG.error("No available TuidFactory Node IDs for IP Address {}", ipAddress);
            System.exit(1);
        }
        Long duration = System.currentTimeMillis() - startTime;
        LOG.info("Auto-assigned TuidFactory Node ID {} for {} {} in {} ms", node.getId(), configuration.getNetworkInterface(), node.getIpAddress(), duration);

        // Initialize the TUID factory for this node in the cluster and register its health check:
        TuidFactory.initialize(node.getId());
        environment.healthChecks().register("tuidFactory", new TuidFactoryHealthCheck());

        // Configure the JSON ObjectMapper
        ObjectMapper mapper = environment.getObjectMapper();
        JsonSerializer.setObjectMapper(mapper);
        JsonSerializer.configureObjectMapper(mapper);

        // Initialize the HTTP Client
        // TODO: use the higher-level JerseyClient instead
        // See: https://jersey.java.net/documentation/latest/client.html
        // Client httpClient = createSSLJerseyClient(environment, configuration.getHttpClient());
        HttpClient httpClient = new HttpClientBuilder(environment).using(configuration.getHttpClient()).build(getName());

        // Initialize the SendGrid Email Client and register its health check:
        SendGridClient.initialize(configuration.getSendgrid());
        environment.healthChecks().register("sendGrid", new SendGridHealthCheck());

        // Initialize the Slack Client
        SlackClient.initialize(configuration.getSlack(), httpClient);
        environment.healthChecks().register("slack", new SlackHealthCheck());

        // Initialize administrative Tasks:
        environment.admin().addTask(new ImportUsersTask());
        environment.admin().addTask(new ImportLicenseFileTask());

        // Initialize database access objects (DAOs):
        startTime = System.currentTimeMillis();
        Events.initialize(cassandraClient, configuration.getEvent());
        Licenses.initialize(cassandraClient);
        Messages.initialize(cassandraClient);
        Metrics.initialize(cassandraClient);
        Tokens.initialize(cassandraClient);
        Users.initialize(cassandraClient);
        duration = System.currentTimeMillis() - startTime;
        LOG.info("Prepared database statements in {} ms", duration);
        Metrics.createMetric(new Metric("init_database", duration));

        // Initialize the worker thread pool and the License Service
        ThreadPoolConfiguration threadPoolConfiguration = configuration.getThreadPool();
        ExecutorServiceBuilder executorServiceBuilder = environment.lifecycle().executorService("worker-pool-%d")
                .workQueue(new ArrayBlockingQueue<>(threadPoolConfiguration.getQueueSize()))
                .minThreads(threadPoolConfiguration.getMinThreads())
                .maxThreads(threadPoolConfiguration.getMaxThreads());
        LicenseService.initialize(executorServiceBuilder.build());

        // Register Resources
        environment.jersey().register(new EventCountResource());
        environment.jersey().register(new EventResource());
        environment.jersey().register(new LicenseResource());
        environment.jersey().register(new MessageResource());
        environment.jersey().register(new MetricResource());
        environment.jersey().register(new MetricTagResource());
        environment.jersey().register(new NodeResource());
        environment.jersey().register(new TokenResource());
        environment.jersey().register(new TuidResource());
        environment.jersey().register(new UserResource());

        // Initialize User Authentication/Authorization and register the associated health check:
        RealmConfiguration realm = configuration.getRealm();
        ApiUserAuthorizer apiUserAuthorizer = new ApiUserAuthorizer();
        // OAuth 2 Bearer Token Authentication
        ApiUserTokenAuthenticator apiUserTokenAuthenticator = new ApiUserTokenAuthenticator();
        CachingAuthenticator<String, User> cachingTokenAuthenticator = new CachingAuthenticator<>(environment.metrics(), apiUserTokenAuthenticator, realm.getAuthenticationCachePolicy());
        OAuthCredentialAuthFilter<User> tokenAuthFilter = new OAuthCredentialAuthFilter.Builder<User>()
                .setAuthenticator(cachingTokenAuthenticator)
                .setAuthorizer(apiUserAuthorizer)
                .setPrefix("Bearer")
                .setRealm(realm.getRealmName())
                .buildAuthFilter();
        // Basic Authentication
        ApiUserBasicAuthenticator apiUserBasicAuthenticator = new ApiUserBasicAuthenticator();
        CachingAuthenticator<BasicCredentials, User> cachingBasicAuthenticator = new CachingAuthenticator<>(environment.metrics(), apiUserBasicAuthenticator, realm.getAuthenticationCachePolicy());
        BasicCredentialAuthFilter<User> basicAuthFilter = new BasicCredentialAuthFilter.Builder<User>()
                .setAuthenticator(cachingBasicAuthenticator)
                .setAuthorizer(apiUserAuthorizer)
                .setPrefix("Basic")
                .setRealm(realm.getRealmName())
                .buildAuthFilter();
        // Chained Authentication
        List<AuthFilter> authFilters = Lists.newArrayList(basicAuthFilter, tokenAuthFilter);
        environment.jersey().register(new AuthDynamicFeature(new ChainedAuthFilter(authFilters)));
        environment.jersey().register(new AuthValueFactoryProvider.Binder<>(User.class));
        environment.jersey().register(RolesAllowedDynamicFeature.class);
        environment.healthChecks().register("securityRealm", new RealmHealthCheck(realm));

        // Configure Cross-Origin Scripting (CORS)
        FilterRegistration.Dynamic filter = environment.servlets().addFilter("CORS", CrossOriginFilter.class);
        filter.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
        filter.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "*");
        filter.setInitParameter(CrossOriginFilter.ALLOWED_HEADERS_PARAM, "*");
        filter.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "GET,PUT,POST,DELETE,OPTIONS,HEAD");
        filter.setInitParameter(CrossOriginFilter.ALLOW_CREDENTIALS_PARAM, "true");
        filter.setInitParameter(CrossOriginFilter.PREFLIGHT_MAX_AGE_PARAM, "3600"); // 1 hour
        filter.setInitParameter(CrossOriginFilter.CHAIN_PREFLIGHT_PARAM, "false");

        // Swagger API Documentation
        SwaggerConfig swaggerConfig = ConfigFactory.config();
        swaggerConfig.setApiVersion(configuration.getVersion());
        swaggerConfig.setBasePath(configuration.getApiBaseUrl());
        swaggerConfig.setApiPath(configuration.getApiBaseUrl());
        ConfigFactory.setConfig(swaggerConfig);
        environment.jersey().register(new SwaggerResource("/"));
        environment.jersey().register(new ApiListingResourceJSON());
        environment.jersey().register(new ApiDeclarationProvider());
        environment.jersey().register(new ResourceListingProvider());
        ScannerFactory.setScanner(new DefaultJaxrsScanner()); // finds resources with @Api Annotations
        ClassReaders.setReader(new DefaultJaxrsApiReader()); // scans resources and extracts resource information
        Metrics.createMetric(new Metric("init_AppraiserService", System.currentTimeMillis() - startAppraiserService));
    }

    /**
     * Create a new JerseyClient with custom (perhaps self-signed) SSL certificates in a Java KeyStore. The KeyStore file
     * is expected to be located in src/main/resources/security. See the README.md file there for more information.
     *
     * @param environment         Dropwizard Environment
     * @param clientConfiguration JerseyClientConfiguration (with settings from a configuration yaml file)
     * @return web services Client
     * @throws Exception
     */
    private Client createSSLJerseyClient(Environment environment, JerseyClientConfiguration clientConfiguration)
            throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException, KeyManagementException {
        final String CERT_FILE = "security/jssecacerts.jks";
        // Load the KeyStore
        KeyStore trustedCertStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustedCertStore.load(Resources.asByteSource(Resources.getResource(CERT_FILE)).openBufferedStream(), null);
        // Initialize the TrustManagerFactory
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustedCertStore);
        // Initialize the SSL Context
        SSLContext sslContext = SSLContext.getInstance(SSLConnectionSocketFactory.TLS);
        sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
        // Create a custom Connection Factory Registry
        Registry<ConnectionSocketFactory> sslSocketRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .register("https", new SSLConnectionSocketFactory(sslContext))
                .build();
        // Return a new Jersey Client
        return new JerseyClientBuilder(environment)
                .using(clientConfiguration)
                .using(sslSocketRegistry)
                .build(getName());
    }

}
