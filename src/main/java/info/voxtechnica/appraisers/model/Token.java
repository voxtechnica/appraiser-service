package info.voxtechnica.appraisers.model;

import com.datastax.driver.core.utils.UUIDs;
import info.voxtechnica.appraisers.config.ApplicationConfiguration;
import lombok.Data;
import org.apache.commons.lang3.ObjectUtils;

import java.util.Comparator;
import java.util.Date;
import java.util.UUID;

/**
 * An 'Token' models an OAuth2 Resource Owner Password Credentials Grant in our system.
 *
 * @see <a href="http://tools.ietf.org/html/rfc6749#section-4.3">IETF RFC 6749 Section 4.3</a>
 */
@Data
public class Token implements Comparable<Token> {
    UUID oAuthToken;
    String userId;
    Date lastUse;
    Long hits;

    public Date getCreatedAt() {
        return new Date(UUIDs.unixTimestamp(oAuthToken));
    }

    /**
     * Check to see if this oAuthToken has expired due to inactivity.
     *
     * @return true if expired; null if unknown
     */
    public Boolean isExpired() {
        long seconds = ApplicationConfiguration.get().getRealm().getTokenExpiration();
        if (seconds == 0) return false; // inactivity expiration disabled
        if (lastUse == null) return null;
        long lastMillis = lastUse.getTime();
        long delta = System.currentTimeMillis() - lastMillis;
        return delta / 1000 > seconds;
    }

    @Override
    public int compareTo(Token that) {
        return Chronological.compare(this, that);
    }

    public static Comparator<Token> Chronological = new Comparator<Token>() {
        @Override
        public int compare(Token one, Token two) {
            if (one == null && two == null) return 0;
            int c0 = one == null ? -1 : (two == null ? 1 : 0);
            if (c0 != 0) return c0;
            return ObjectUtils.compare(one.getCreatedAt(), two.getCreatedAt());
        }
    };

    public static Comparator<Token> ReverseChronological = new Comparator<Token>() {
        @Override
        public int compare(Token one, Token two) {
            return Chronological.compare(two, one);
        }
    };

}
