package info.voxtechnica.appraisers.config;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration settings for the Slack messaging service
 */
@Data
public class SlackConfiguration {
    private String webhookUri = "https://hooks.slack.com/services/XXXXXXXXX/XXXXXXXXX/XXXXXXXXXXXXXXXXXXXXXXXX";
    private String channel = "#ops";
    private List<String> notifyOnError = new ArrayList<>();
    private Boolean enabled = false;

    public Boolean isEnabled() {
        return enabled != null && enabled;
    }
}
