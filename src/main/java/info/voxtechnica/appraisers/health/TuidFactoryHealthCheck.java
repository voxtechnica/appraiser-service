package info.voxtechnica.appraisers.health;

import com.codahale.metrics.health.HealthCheck;
import info.voxtechnica.appraisers.util.TuidFactory;

public class TuidFactoryHealthCheck extends HealthCheck {
    @Override
    protected Result check() throws Exception {
        return TuidFactory.isInitialized() ? Result.healthy("nodeId=" + TuidFactory.getServerId()) : Result.unhealthy("TuidFactory not initialized.");
    }
}
