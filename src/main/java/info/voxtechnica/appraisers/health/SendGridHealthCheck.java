package info.voxtechnica.appraisers.health;

import com.codahale.metrics.health.HealthCheck;
import info.voxtechnica.appraisers.client.SendGridClient;

public class SendGridHealthCheck extends HealthCheck {
    @Override
    protected Result check() throws Exception {
        if (SendGridClient.isInitialized()) {
            StringBuilder message = new StringBuilder();
            message.append("enabled=");
            message.append(SendGridClient.isEnabled());
            if (SendGridClient.isEnabled()) {
                message.append(" username=");
                message.append(SendGridClient.getUsername());
                message.append(" limitSending=");
                message.append(SendGridClient.getLimitSending());
            }
            return Result.healthy(message.toString());
        } else return Result.unhealthy("initialized=false");
    }
}
