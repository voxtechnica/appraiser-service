package info.voxtechnica.appraisers.health;

import com.codahale.metrics.health.HealthCheck;
import info.voxtechnica.appraisers.client.SlackClient;

public class SlackHealthCheck extends HealthCheck {
    @Override
    protected Result check() throws Exception {
        if (SlackClient.isInitialized()) {
            StringBuilder message = new StringBuilder();
            message.append("enabled=");
            message.append(SlackClient.isEnabled());
            if (SlackClient.isEnabled()) {
                message.append(" channel=");
                message.append(SlackClient.getChannel());
                message.append(" notifyOnError=");
                message.append(String.join(",", SlackClient.getNotifyOnError()));
            }
            return Result.healthy(message.toString());
        } else return Result.unhealthy("initialized=false");
    }
}
