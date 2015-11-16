package info.voxtechnica.appraisers.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * AccessToken is a Bearer Token Response, whereas {@link Token} models the OAuth2 token in our system.
 * OAuth2 Resource Owner Password Credentials Grant
 *
 * @see <a href="http://tools.ietf.org/html/rfc6749#section-4.3">IETF RFC 6749 Section 4.3</a>
 * <p>
 * We use snake_case in the JsonProperty, only because the specification requires it (section 4.3.3).
 */
@Data
public class AccessToken {
    @JsonProperty("token_type")
    @NotNull
    private String tokenType = "bearer";

    @JsonProperty("access_token")
    @NotNull
    private String accessToken;

    public AccessToken() {
    }

    public AccessToken(String accessToken) {
        this.accessToken = accessToken;
    }
}