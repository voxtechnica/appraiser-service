package info.voxtechnica.appraisers.db;

import com.google.common.io.Resources;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.io.util.FileUtils;
import org.apache.cassandra.service.CassandraDaemon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * The embedded Cassandra server is useful for development and unit testing data access objects (DAOs) without
 * requiring that Cassandra be installed on the build machine. This implementation uses a directory for temporary files
 * and assumes the existence of matching configuration file resources in the class path.
 */
public class CassandraEmbedded {
    private static final Logger LOG = LoggerFactory.getLogger(CassandraEmbedded.class);
    private static CassandraDaemon daemon = null;

    /**
     * Start an embedded Cassandra server, spawning a new thread.
     *
     * @throws IOException
     */
    public static void start() throws IOException {
        // Cassandra may already be running; there can be only one instance in the JVM.
        if (daemon == null) {
            LOG.info("Starting embedded Cassandra...");

            // create a directory for temporary files needed by Cassandra. Make sure that cassandra.yaml contains
            // directives to store all files here, including data files, commit logs, and saved caches.
            File cassandraDir = new File("cassandra");
            if (!cassandraDir.exists()) FileUtils.createDirectory(cassandraDir);
            File triggerDir = new File("cassandra/triggers");
            if (!triggerDir.exists()) FileUtils.createDirectory(triggerDir);
            System.setProperty("cassandra.triggers_dir", triggerDir.getAbsolutePath());

            // Cassandra configuration file
            File serverConfigFile = new File("cassandra/cassandra.yaml");
            if (serverConfigFile.createNewFile()) {
                // if the file didn't already exist, then copy the Cassandra configuration from static resources
                try (FileOutputStream outputStream = new FileOutputStream(serverConfigFile)) {
                    URL serverConfigURL = Resources.getResource("config/cassandra.yaml");
                    Resources.copy(serverConfigURL, outputStream);
                }
            }
            System.setProperty("cassandra.config", "file:" + serverConfigFile.getAbsolutePath());
            System.setProperty("cassandra-foreground", "true");

            // create subdirectories needed for Cassandra:
            DatabaseDescriptor.createAllDirectories();

            // configure Java management extensions (JMX):
            System.setProperty("cassandra.jmx.local.port", "7199");

            // launch the Cassandra daemon:
            final CountDownLatch startupLatch = new CountDownLatch(1);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    daemon = new CassandraDaemon();
                    daemon.activate();
                    startupLatch.countDown();
                }
            });
            try {
                startupLatch.await(10, SECONDS);
            } catch (InterruptedException e) {
                LOG.error("Interrupted waiting for the Cassandra daemon to start:", e);
                throw new AssertionError(e);
            }

        } else if (!daemon.nativeServer.isRunning()) {
            daemon.nativeServer.start();
        }
    }

    /**
     * Stop the embedded Cassandra server.
     */
    public static void stop() {
        daemon.nativeServer.stop();
    }

    /**
     * Is the embedded Cassandra native CQL server running?
     *
     * @return whether the embedded Cassandra native CQL server is running
     */
    public static boolean isRunning() {
        return (daemon != null && daemon.nativeServer.isRunning());
    }

}
