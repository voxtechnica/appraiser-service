package info.voxtechnica.appraisers.db.policy;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.WriteType;
import com.datastax.driver.core.policies.RetryPolicy;

public class SimpleRetryPolicy implements RetryPolicy {
    private int readRetries = 3;
    private int writeRetries = 3;

    public SimpleRetryPolicy() {
    }

    public SimpleRetryPolicy(int readRetries, int writeRetries) {
        this.readRetries = readRetries;
        this.writeRetries = writeRetries;
    }

    @Override
    public void init(Cluster cluster) {
    }

    @Override
    public void close() {
    }

    @Override
    public RetryDecision onReadTimeout(final Statement query, final ConsistencyLevel cl, final int requiredResponses,
                                       final int receivedResponses, final boolean dataRetrieved, final int nbRetry) {
        if (nbRetry >= readRetries) return RetryDecision.rethrow();
        return receivedResponses >= requiredResponses && !dataRetrieved ? RetryDecision.retry(cl) : RetryDecision.rethrow();
    }

    @Override
    public RetryDecision onWriteTimeout(final Statement query, final ConsistencyLevel cl, final WriteType writeType,
                                        final int requiredAcks, final int receivedAcks, final int nbRetry) {
        if (nbRetry >= writeRetries) return RetryDecision.rethrow();
        return RetryDecision.retry(cl);
    }

    @Override
    public RetryDecision onUnavailable(final Statement query, final ConsistencyLevel cl, final int requiredReplica,
                                       final int aliveReplica, final int nbRetry) {
        return RetryDecision.rethrow();
    }

    public int getReadRetries() {
        return readRetries;
    }

    public void setReadRetries(int readRetries) {
        this.readRetries = readRetries;
    }

    public int getWriteRetries() {
        return writeRetries;
    }

    public void setWriteRetries(int writeRetries) {
        this.writeRetries = writeRetries;
    }
}
