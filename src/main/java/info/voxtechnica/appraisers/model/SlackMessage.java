package info.voxtechnica.appraisers.model;

import info.voxtechnica.appraisers.client.SlackClient;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Slack Message Formatting Reference: https://api.slack.com/docs/attachments
 */
@Data
public class SlackMessage {
    private String channel;
    private List<Attachment> attachments = new ArrayList<>();

    public SlackMessage() {
    }

    public SlackMessage(Message message) {
        channel = SlackClient.getChannel();
        Attachment attachment = new Attachment(message);
        attachments.add(attachment);
    }

    @Data
    public class Attachment {
        private String fallback;
        private String color;
        private String pretext;
        private String title;
        private String title_link;
        private String text;
        private List<String> mrkdwn_in = new ArrayList<>();

        public Attachment() {
        }

        public Attachment(Message message) {
            if (message.getLogLevel() != null) {
                switch (message.getLogLevel()) {
                    case ERROR:
                        color = "danger";
                        break;
                    case WARN:
                        color = "warning";
                        break;
                    default:
                        color = "good";
                }
            }
            if (Event.LogLevel.ERROR.equals(message.getLogLevel()) || message.isNotify())
                message.getRecipients().addAll(SlackClient.getNotifyOnError());
            if (!message.getRecipients().isEmpty()) pretext = "Attention: " + String.join(" ", message.getRecipients());
            title = message.getSubject();
            title_link = message.getLink();
            text = message.getBody();
            mrkdwn_in.add("text");
            mrkdwn_in.add("pretext");
            fallback = String.format("%s: %s\n%s", title, title_link, text);
        }
    }
}
