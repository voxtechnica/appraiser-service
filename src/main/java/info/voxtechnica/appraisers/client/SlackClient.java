package info.voxtechnica.appraisers.client;

import info.voxtechnica.appraisers.config.SlackConfiguration;
import info.voxtechnica.appraisers.db.dao.Events;
import info.voxtechnica.appraisers.db.dao.Metrics;
import info.voxtechnica.appraisers.model.Message;
import info.voxtechnica.appraisers.model.Metric;
import info.voxtechnica.appraisers.model.SlackMessage;
import info.voxtechnica.appraisers.util.JsonSerializer;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 * The SlackClient is used to send messages to Slack.
 * <p>
 * Reference: https://api.slack.com/incoming-webhooks
 */
public class SlackClient {
    private static final Logger LOG = LoggerFactory.getLogger(SlackClient.class);
    private static boolean initialized = false;
    private static boolean enabled = false; // disabled until initialized
    private static HttpClient http;
    private static URI webhook;
    private static String channel;
    private static List<String> notifyOnError;

    public static void initialize(SlackConfiguration configuration, HttpClient httpClient) throws URISyntaxException {
        if (!initialized) {
            long startTime = System.currentTimeMillis();
            enabled = configuration.isEnabled();
            if (enabled) {
                http = httpClient;
                webhook = new URI(configuration.getWebhookUri());
                channel = configuration.getChannel();
                notifyOnError = configuration.getNotifyOnError();
            }
            initialized = true;
            Long duration = System.currentTimeMillis() - startTime;
            StringBuilder message = new StringBuilder();
            message.append("Initialized in ");
            message.append(duration);
            message.append(" ms: enabled=");
            message.append(enabled);
            if (enabled) {
                message.append(" channel=");
                message.append(channel);
                message.append(" notifyOnError=");
                message.append(String.join(",", notifyOnError));
            }
            LOG.info(message.toString());
        } else LOG.info("Previously initialized");
    }

    public static Message.Status send(Message message) {
        if (!enabled) {
            Events.info(null, message.getId(), String.format("SlackClient: disabled. Message %s not sent.", message.getId()));
            return Message.Status.NOT_SENT;
        }
        try {
            long startTime = System.currentTimeMillis();
            SlackMessage msg = new SlackMessage(message);
            StringEntity json = new StringEntity(JsonSerializer.getJson(msg));
            json.setContentType("application/json");
            HttpPost request = new HttpPost(webhook);
            request.setEntity(json);
            HttpResponse response = http.execute(request);
            if (200 != response.getStatusLine().getStatusCode()) {
                Events.error(null, message.getId(), String.format("SlackClient: Error %d sending message %s", response.getStatusLine().getStatusCode(), message.getId()), null);
                return Message.Status.ERROR;
            }
            Metrics.createMetric(new Metric("slack", message.getId(), System.currentTimeMillis() - startTime));
            return Message.Status.SENT;
        } catch (IOException e) {
            Events.error(null, message.getId(), "SlackClient: " + ExceptionUtils.getRootCauseMessage(e), ExceptionUtils.getStackTrace(e));
            return Message.Status.ERROR;
        }
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static URI getWebhook() {
        return webhook;
    }

    public static String getChannel() {
        return channel;
    }

    public static List<String> getNotifyOnError() {
        return notifyOnError;
    }
}
