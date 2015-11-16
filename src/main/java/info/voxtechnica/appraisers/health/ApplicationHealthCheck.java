package info.voxtechnica.appraisers.health;

import com.codahale.metrics.health.HealthCheck;
import info.voxtechnica.appraisers.config.ApplicationConfiguration;

public class ApplicationHealthCheck extends HealthCheck {

    @Override
    protected Result check() throws Exception {
        return ApplicationConfiguration.isInitialized() ? Result.healthy("version=" + ApplicationConfiguration.get().getVersion()) : Result.unhealthy("not initialized");
    }

}
