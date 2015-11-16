package info.voxtechnica.appraisers.config;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration Settings for the SendGrid email service
 */
@Data
public class SendGridConfiguration {
    private String username;
    private String password;
    private String from = "admin@voxtechnica.info";
    private String fromName = "VoxTechnica";
    private List<String> recipientsBcc = new ArrayList<>();
    private List<String> recipientDomains = new ArrayList<>();
    private Boolean enabled = false;

    public Boolean isEnabled() {
        return enabled != null && enabled;
    }
}
