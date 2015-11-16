package info.voxtechnica.appraisers.auth;

import com.google.common.base.Optional;
import info.voxtechnica.appraisers.config.ApplicationConfiguration;
import info.voxtechnica.appraisers.db.dao.Tokens;
import info.voxtechnica.appraisers.db.dao.Users;
import info.voxtechnica.appraisers.model.Token;
import info.voxtechnica.appraisers.model.User;
import io.dropwizard.auth.AuthenticationException;
import io.dropwizard.auth.Authenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.UUID;

/**
 * The ApiUserTokenAuthenticator authenticates user identity with OAuth2 Bearer Tokens, returning a valid User on success.
 * Users can be either humans, with credentials stored in the database, or servers, with credentials stored in the
 * application configuration file.
 */
public class ApiUserTokenAuthenticator implements Authenticator<String, User> {
    private static final Logger LOG = LoggerFactory.getLogger(ApiUserTokenAuthenticator.class);
    private final HashMap<String, User> apiUsers = new HashMap<>();

    public ApiUserTokenAuthenticator() {
        // server and bootstrap accounts are managed in the configuration file
        for (User user : ApplicationConfiguration.get().getRealm().getApiUsers())
            if (user.isActiveUser())
                apiUsers.put(user.getOauthToken().toString(), user);
        LOG.info("Initialized Token Authenticator with {} active API users", apiUsers.size());
    }

    @Override
    public Optional<User> authenticate(final String token) throws AuthenticationException {
        // check credentials for users added at boot time from configuration file:
        if (apiUsers.containsKey(token)) return Optional.of(apiUsers.get(token));
        // check credentials for users in the database:
        try {
            Token oAuthToken = Tokens.readOAuthToken(UUID.fromString(token));
            if (oAuthToken == null || oAuthToken.isExpired()) return Optional.absent();
            User user = Users.readUser(oAuthToken.getUserId());
            if (user != null && user.isActiveUser()) {
                Tokens.updateUserToken(oAuthToken.getUserId(), oAuthToken.getOAuthToken());
                user.setOauthToken(oAuthToken.getOAuthToken()); // let the resource know which token they used here
                return Optional.of(user);
            }
        } catch (Exception e) {
            LOG.debug("Authentication Failure: OAuth Token {} failed check", token);
            throw new AuthenticationException(e);
        }
        LOG.debug("Authentication Failure: OAuth Token {} not found", token);
        return Optional.absent();
    }
}