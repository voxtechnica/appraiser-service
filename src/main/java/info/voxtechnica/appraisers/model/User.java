package info.voxtechnica.appraisers.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import lombok.Data;
import org.apache.commons.lang3.ObjectUtils;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.security.auth.Subject;
import javax.validation.constraints.NotNull;
import java.security.Principal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A ‘user’ is a human or another computer process that invokes the API. It is a focal point for access controls
 * and event logging. Users may be found in either the yaml configuration file or in the database. For rapid
 * server-to-server interaction, the configuration file is preferred for in-memory performance reasons.
 */
@Data
public class User implements Principal, Comparable<User> {
    private String id;
    private String updateId;
    private String email;
    private String password; // transient; not to be stored in the database
    private String passwordHash;
    private String resetToken; // password reset
    private UUID oauthToken; // OAuth 2 Bearer Token for Bootstrap Accounts
    private Set<String> roles;
    private Status status;

    public String getCreatedAt() {
        return id == null ? null : (new Tuid(id)).getCreatedAt();
    }

    public String getUpdatedAt() {
        return updateId == null ? null : (new Tuid(updateId)).getCreatedAt();
    }

    /**
     * Check the user's OAuth 2 Bearer Token against a provided token
     *
     * @param token OAuth 2 Bearer Token (Type 1 UUID)
     * @return true if the tokens match
     */
    public Boolean checkOAuthToken(UUID token) {
        if (token == null || oauthToken == null) return false;
        return oauthToken.equals(token);
    }

    /**
     * Check the user's password against a provided secret.
     *
     * @return true if the hashed password matches the passwordHash on record.
     */
    public Boolean checkPassword(String secret) {
        String clearText = id + secret;
        String hash = Hashing.sha256().hashString(clearText, Charsets.UTF_8).toString();
        return passwordHash.equals(hash);
    }

    /**
     * Hash the user's password, store it, and remove the password.
     */
    public void hashPassword() {
        if (id == null || password == null) throw new IllegalStateException("PasswordHash: missing id or password.");
        passwordHash = Hashing.sha256().hashString(id + password, Charsets.UTF_8).toString();
        password = null;
    }

    /**
     * Check for a valid email address
     *
     * @return True if valid email address
     */
    public Boolean validEmail() {
        return validEmail(email);
    }

    public static Boolean validEmail(String email) {
        if (email == null) return false;
        try {
            InternetAddress emailAddress = new InternetAddress(email);
            emailAddress.validate();
            return true;
        } catch (AddressException e) {
            return false;
        }
    }

    /**
     * Get the User's email domain name. It could be useful for grouping Users by Organization.
     *
     * @return email domain name (e.g. "example.com")
     */
    public String getDomain() {
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

    public Set<String> addRole(@NotNull String role) {
        if (roles == null) roles = new HashSet<>();
        roles.add(role);
        return roles;
    }

    public Boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    @JsonIgnore
    public Boolean isAdmin() {
        return roles != null && roles.contains("admin");
    }

    @JsonIgnore
    public Boolean isActiveUser() {
        return Status.DISABLED != status;
    }

    @Override
    public String getName() {
        return id == null ? email : id;
    }

    @Override
    public boolean implies(Subject subject) {
        String name = getName();
        Set<Principal> principals = subject.getPrincipals();
        if (principals != null)
            for (Principal principal : principals)
                if (name != null && name.equalsIgnoreCase(principal.getName())) return true;
        return false;
    }

    @Override
    public int compareTo(User that) {
        return Chronological.compare(this, that);
    }

    public static Comparator<User> Chronological = new Comparator<User>() {
        @Override
        public int compare(User one, User two) {
            if (one == null && two == null) return 0;
            int c0 = one == null ? -1 : (two == null ? 1 : 0);
            if (c0 != 0) return c0;
            int c1 = ObjectUtils.compare(one.getId(), two.getId());
            if (c1 != 0) return c1;
            return ObjectUtils.compare(one.getUpdateId(), two.getUpdateId());
        }
    };

    public static Comparator<User> ReverseChronological = new Comparator<User>() {
        @Override
        public int compare(User one, User two) {
            return Chronological.compare(two, one);
        }
    };

    public static Comparator<User> Alphabetical = new Comparator<User>() {
        @Override
        public int compare(User one, User two) {
            if (one == null && two == null) return 0;
            int c0 = one == null ? -1 : (two == null ? 1 : 0);
            if (c0 != 0) return c0;
            return ObjectUtils.compare(one.getEmail(), two.getEmail());
        }
    };

    public enum Status {DISABLED, ENABLED, PENDING}

}
