package info.voxtechnica.appraisers.util;

import info.voxtechnica.appraisers.model.Tuid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This factory generates a time-based cluster-unique identifier (TUID) that includes the creation time with millisecond
 * resolution, along with 18 bits of entropy to ensure uniqueness. The resulting ID is a simple 64-bit long integer that
 * can be sorted chronologically. Expressed in base 36, it becomes a 12-digit string (e.g. 2TNSOS9L376Y).
 */
public class TuidFactory {
    private static final Logger LOG = LoggerFactory.getLogger(TuidFactory.class);
    private static final Object sync = new Object();
    private static long counter = 0;
    private static long counterpeg = 0;
    private static boolean initialized = false;
    private static byte serverId = 0;

    /**
     * serverId must be unique across a cluster (1 per app server). It's used to ensure the cluster-uniqueness of TUIDs.
     *
     * @param serverId Unique Server Node ID
     */
    public static void initialize(final byte serverId) {
        if (!initialized) {
            long startTime = System.currentTimeMillis();
            TuidFactory.serverId = serverId;
            TuidFactory.initialized = true;
            Long duration = System.currentTimeMillis() - startTime;
            LOG.info("Initialized in {} ms: nodeId={}", duration, getServerId());
        } else LOG.info("Previously initialized: nodeId={}", getServerId());
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static byte getServerId() {
        return TuidFactory.serverId;
    }

    /**
     * This time-sortable ID supports up to 1024 sequential IDs per millisecond from a single JVM. It requires each
     * server to maintain a unique 8-bit identifier (see initialize())
     *
     * @return TUID (Time-Sequenced Unique long integer ID)
     */
    public static String getId() {
        return getId(0);
    }

    public static String getId(final long offset) {
        if (!TuidFactory.initialized) throw new IllegalStateException("TuidFactory not initialized.");
        long id = System.currentTimeMillis();
        synchronized (sync) {
            if (counterpeg != id) {
                counterpeg = id;
                counter = 0;
            } else if (counter == 1023) {
                // Throttle to about 1M per second per server.
                try {
                    // It's necessary to remain blocked, even if it looks inefficient for multi-threading.
                    while (System.currentTimeMillis() == id) {
                        // Guarantee a different currentTimeMillis();
                        Thread.sleep(0, 50000);
                    }
                } catch (InterruptedException ex) {
                    // resume
                }
                counterpeg = id;
                counter = 0;
            }
            id = (((id + offset) << 18) & 0xFFFFFFFFFFFC0000L) | ((counter++ << 8) & 0x000000000003FF00L) | (serverId & 0x00000000000000FFL);
        }
        return Long.toString(id, 36).toUpperCase(); // Convert to 12-digit base 36 String
    }

    public static String getFirstId(final long offsetMillis) {
        long id = ((System.currentTimeMillis() + offsetMillis) << 18) & 0xFFFFFFFFFFFC0000L;
        return Long.toString(id, 36).toUpperCase();
    }
    
    public static String getLastId(final long offsetMillis) {
        long id = ((System.currentTimeMillis() + offsetMillis) << 18) | 0x000000000003FFFFL;
        return Long.toString(id, 36).toUpperCase();
    }
    
    public static String getIdFromTimestamp(final long millis) {
        if (!TuidFactory.initialized) throw new IllegalStateException("TuidFactory not initialized.");
        long id = millis;
        synchronized (sync) {
            // note: this version can wrap around at > 1024 IDs/millisecond
            if (counterpeg != id) {
                counterpeg = id;
                counter = 0;
            }
            id = ((id << 18) & 0xFFFFFFFFFFFC0000L) | ((counter++ << 8) & 0x000000000003FF00L) | (serverId & 0x00000000000000FFL);
        }
        return Long.toString(id, 36).toUpperCase(); // Convert to 12-digit base 36 String
    }

    public static String getFirstIdFromTimestamp(final long millis) {
        long id = (millis << 18) & 0xFFFFFFFFFFFC0000L;
        return Long.toString(id, 36).toUpperCase();
    }

    public static String getLastIdFromTimestamp(final long millis) {
        long id = (millis << 18) | 0x000000000003FFFFL;
        return Long.toString(id, 36).toUpperCase();
    }

    public static Tuid generateTuid() {
        return new Tuid(getId());
    }

    public static Tuid generateTuid(final long offset) {
        return new Tuid(getId(offset));
    }
}