package info.voxtechnica.appraisers.command;

import info.voxtechnica.appraisers.client.CassandraClient;
import info.voxtechnica.appraisers.config.ApplicationConfiguration;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Create a clean, empty database for the keyspace indicated in the application configuration file. You can delete an
 * existing keyspace with --drop-keyspace.
 */
public class SchemaCommand extends ConfiguredCommand<ApplicationConfiguration> {

    private static final Logger LOG = LoggerFactory.getLogger(SchemaCommand.class);

    public SchemaCommand() {
        super("schema", "Create or update the database schema.");
    }

    @Override
    public void configure(Subparser subparser) {
        super.configure(subparser);
        subparser.addArgument("-k", "--drop-keyspace")
                .action(Arguments.storeTrue())
                .dest("drop-keyspace")
                .help("Drop the configured keyspace, killing any existing data.");
    }

    @Override
    protected void run(Bootstrap<ApplicationConfiguration> bootstrap, Namespace namespace, ApplicationConfiguration configuration) throws Exception {
        CassandraClient cassandraClient = new CassandraClient(configuration.getCassandra());

        // Drop the keyspace if they said to start fresh
        if (cassandraClient.keyspaceExists() && namespace.getBoolean("drop-keyspace")) {
            LOG.info("Dropping keyspace: {}", cassandraClient.getKeyspaceName());
            cassandraClient.dropKeyspace();
        }

        // Create keyspace if missing
        LOG.info("Creating keyspace {} if missing...", cassandraClient.getKeyspaceName());
        cassandraClient.createKeyspace();
        LOG.info("Complete. Tables are created/updated automatically by launching the service with createIfMissing: true");

        // Shut down and exit
        cassandraClient.stop();
        System.exit(0);
    }
}