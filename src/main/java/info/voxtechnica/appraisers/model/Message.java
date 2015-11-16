package info.voxtechnica.appraisers.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import info.voxtechnica.appraisers.config.ApplicationConfiguration;
import lombok.Data;
import org.apache.commons.lang3.ObjectUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A ‘message’ is a notification of some sort. It could be delivered by email, SMS, HipChat, or through a call-back
 * service, using our own protocol and message format. Only administrators can send messages through the API, but users
 * can see their own messages. Usually, the Message Service will be used internally by other services for things like
 * sending password reset messages and verifying email addresses.
 */
@Data
public class Message implements Comparable<Message> {
    private String id;
    private String updateId;
    private List<String> recipients = new ArrayList<>(); // email addresses, slack users, etc.
    private List<String> recipientsBcc;
    private Boolean notify; // should we notify our operations staff?
    private String from; // email address, slack user, etc.
    private String subject = ApplicationConfiguration.get().getSendgrid().getFromName(); // email subject or message title
    private String link; // optional hyperlink to the point of the message
    private String body;
    private Type type = Type.EMAIL;
    private Event.LogLevel logLevel;
    private Status status = Status.INITIALIZED;

    public Message() {
    }

    public Message(Type type, String from, List<String> recipients, String subject, String body) {
        this.type = type;
        this.from = from;
        this.recipients = recipients;
        this.subject = subject;
        this.body = body;
    }

    public Message(Type type, String from, List<String> recipients, List<String> recipientsBcc, String subject, String body) {
        this.type = type;
        this.from = from;
        this.recipients = recipients;
        this.recipientsBcc = recipientsBcc;
        this.subject = subject;
        this.body = body;
    }

    public Message(Type type, Event.LogLevel logLevel, String subject, String link, String body) {
        this.type = type;
        this.logLevel = logLevel;
        this.subject = subject;
        this.link = link;
        this.body = body;
    }

    public Message(Type type, Event.LogLevel logLevel, Boolean notify, String subject, String link, String body) {
        this.type = type;
        this.logLevel = logLevel;
        this.notify = notify;
        this.subject = subject;
        this.link = link;
        this.body = body;
    }

    @JsonIgnore
    public Boolean isNotify() {
        return notify != null && notify;
    }

    @JsonIgnore
    public Boolean isParticipant(String address) {
        if (recipients != null)
            for (String recipient : recipients)
                if (recipient.equalsIgnoreCase(address))
                    return true;
        return from != null && from.equalsIgnoreCase(address);
    }

    public List<String> addRecipient(String address) {
        recipients.add(address);
        return recipients;
    }

    public List<String> addRecipientBcc(String address) {
        if (recipientsBcc == null) recipientsBcc = new ArrayList<>();
        recipientsBcc.add(address);
        return recipientsBcc;
    }

    @Override
    public int compareTo(Message that) {
        return Chronological.compare(this, that);
    }

    public static Comparator<Message> Chronological = new Comparator<Message>() {
        @Override
        public int compare(Message one, Message two) {
            if (one == null && two == null) return 0;
            int c0 = one == null ? -1 : (two == null ? 1 : 0);
            if (c0 != 0) return c0;
            int c1 = ObjectUtils.compare(one.getId(), two.getId());
            if (c1 != 0) return c1;
            return ObjectUtils.compare(one.getUpdateId(), two.getUpdateId());
        }
    };

    public static Comparator<Message> ReverseChronological = new Comparator<Message>() {
        @Override
        public int compare(Message one, Message two) {
            return Chronological.compare(two, one);
        }
    };

    /**
     * Message types could be expanded to include SMS, Amazon SNS, etc.
     */
    public enum Type {
        EMAIL, SLACK
    }

    /**
     * Message Status NOT_SENT is like SENT, except delivery was disabled (e.g. non-production environment)
     */
    public enum Status {
        INITIALIZED, SENT, NOT_SENT, ERROR
    }
}
