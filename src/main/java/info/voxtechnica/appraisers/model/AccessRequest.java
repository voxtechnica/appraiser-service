package info.voxtechnica.appraisers.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * The AccessRequest provides a loose interpretation of the OAuth 2 Specification... "loose", because:
 * (1) we're using camelCase instead of snake_case, and
 * (2) we're allowing Content-Type application/json instead of application/x-www-form-urlencoded
 * We have another resource method for the strict implementation.
 *
 * @see <a href="http://tools.ietf.org/html/rfc6749#section-4.3.2">IETF RFC 6749 Section 4.3.2</a>
 */
@Data
public class AccessRequest {
    @JsonProperty
    @NotNull
    private String grantType = "password";

    @JsonProperty
    @NotNull
    private String username;

    @JsonProperty
    private String password;
}
