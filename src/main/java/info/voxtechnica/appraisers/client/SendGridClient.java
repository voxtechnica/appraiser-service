package info.voxtechnica.appraisers.client;

import com.sendgrid.SendGrid;
import com.sendgrid.SendGridException;
import info.voxtechnica.appraisers.config.SendGridConfiguration;
import info.voxtechnica.appraisers.db.dao.Events;
import info.voxtechnica.appraisers.db.dao.Metrics;
import info.voxtechnica.appraisers.model.Message;
import info.voxtechnica.appraisers.model.Metric;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The SendGrid Client sends email messages. Reference: https://sendgrid.com/docs/API_Reference/Web_API/mail.html
 */
public class SendGridClient {
    private static final Logger LOG = LoggerFactory.getLogger(SendGridClient.class);
    private static Boolean initialized = false;
    private static Boolean enabled = false; // disabled until initialized
    private static String username;
    private static String password;
    private static String from;
    private static String fromName;
    private static List<String> recipientsBcc;
    private static List<String> recipientDomains;
    private static SendGrid sendgrid;
    private static Boolean limitSending;
    
    public static void initialize(SendGridConfiguration config) {
        if (!initialized) {
            long startTime = System.currentTimeMillis();
            enabled = config.isEnabled();
            if (enabled) {
                username = config.getUsername();
                password = config.getPassword();
                from = config.getFrom();
                fromName = config.getFromName();
                recipientsBcc = config.getRecipientsBcc();
                recipientDomains = config.getRecipientDomains();
                limitSending = recipientDomains != null && !recipientDomains.contains("any");
                sendgrid = new SendGrid(username, password);
            }
            initialized = true;
            Long duration = System.currentTimeMillis() - startTime;
            StringBuilder message = new StringBuilder();
            message.append("Initialized in ");
            message.append(duration);
            message.append(" ms: enabled=");
            message.append(enabled);
            if (enabled) {
                message.append(" username=");
                message.append(username);
                message.append(" limitSending=");
                message.append(limitSending);
            }
            LOG.info(message.toString());
        } else LOG.info("Previously initialized");
    }

    public static Message.Status send(Message message) {
        if (!enabled) {
            Events.info(null, message.getId(), String.format("SendGridClient: disabled. Message %s not sent.", message.getId()));
            return Message.Status.NOT_SENT;
        }
        long startTime = System.currentTimeMillis();
        // Filter list of recipients to allowed domains
        ArrayList<String> recipients = new ArrayList<>();
        if (limitSending) for (String email : message.getRecipients()) {
            String domain = getDomain(email);
            if (domain != null && recipientDomains.contains(domain))
                recipients.add(email);
        } else recipients.addAll(message.getRecipients());
        if (recipients.isEmpty()) return Message.Status.NOT_SENT;
        // Send Email
        SendGrid.Email email = new SendGrid.Email();
        email.addTo(recipients.toArray(new String[recipients.size()]));
        if (message.getRecipientsBcc() != null)
            email.addBcc(message.getRecipientsBcc().toArray(new String[message.getRecipientsBcc().size()]));
        email.setFrom(message.getFrom() == null ? from : message.getFrom());
        email.setFromName(fromName);
        email.setSubject(message.getSubject());
        email.setText(message.getBody());
        // TODO: enable HTML messages with email.setHtml()
        // TODO: enable Attachments with email.addAttachment() and email.addContentId()
        try {
            SendGrid.Response response = sendgrid.send(email);
            if (response.getStatus()) {
                Metrics.createMetric(new Metric("sendgrid", message.getId(), System.currentTimeMillis() - startTime));
                return Message.Status.SENT;
            }
            else {
                Events.error(null, message.getId(), String.format("SendGridClient: Error sending Message %s: code=%d message=%s", message.getId(), response.getCode(), response.getMessage()), null);
                return Message.Status.ERROR;
            }
        } catch (SendGridException | IOException e) {
            Events.error(null, message.getId(), String.format("SendGridClient: Error sending Message %s", message.getId()), ExceptionUtils.getStackTrace(e));
            return Message.Status.ERROR;
        }
    }

    private static String getDomain(String email) {
        if (email == null) return null;
        try {
            InternetAddress emailAddress = new InternetAddress(email);
            emailAddress.validate();
            String address = emailAddress.getAddress();
            return address.substring(address.indexOf('@') + 1);
        } catch (AddressException e) {
            return null;
        }
    }

    public static Boolean isInitialized() {
        return initialized;
    }

    public static Boolean isEnabled() {
        return enabled;
    }

    public static String getUsername() {
        return username;
    }

    public static Boolean getLimitSending() {
        return limitSending;
    }

    public static String getFrom() {
        return from;
    }

    public static String getFromName() {
        return fromName;
    }

    public static List<String> getRecipientsBcc() {
        return recipientsBcc;
    }

    public static List<String> getRecipientsBccNoSMS() {
        // Filter out SMS recipients
        List<String> recipients = new ArrayList<>();
        for (String recipient : recipientsBcc)
            if (!recipient.endsWith("@txt.att.net")) recipients.add(recipient); // Filter out SMS recipients
        return recipients;
    }

    public static List<String> getRecipientDomains() {
        return recipientDomains;
    }
}
