package info.voxtechnica.appraisers.auth;

import info.voxtechnica.appraisers.config.ApplicationConfiguration;
import info.voxtechnica.appraisers.db.dao.Users;
import info.voxtechnica.appraisers.model.User;
import io.dropwizard.auth.AuthenticationException;
import io.dropwizard.auth.Authenticator;
import io.dropwizard.auth.basic.BasicCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Optional;

/**
 * The ApiUserBasicAuthenticator authenticates user identity with Basic Authentication, returning a valid User on success.
 * Users can be either humans, with credentials stored in the database, or servers, with credentials stored in the
 * application configuration file.
 */
public class ApiUserBasicAuthenticator implements Authenticator<BasicCredentials, User> {
    private static final Logger LOG = LoggerFactory.getLogger(ApiUserBasicAuthenticator.class);
    private final HashMap<String, User> apiUsers = new HashMap<>();

    public ApiUserBasicAuthenticator() {
        // server and bootstrap accounts are managed in the configuration file
        for (User user: ApplicationConfiguration.get().getRealm().getApiUsers())
            if (user.isActiveUser())
                apiUsers.put(user.getId(), user);
        LOG.info("Initialized Basic Authenticator with {} active API users", apiUsers.size());
    }

    @Override
    public Optional<User> authenticate(final BasicCredentials credentials) throws AuthenticationException {
        // check credentials for users added at boot time from configuration file:
        if (apiUsers.containsKey(credentials.getUsername())) {
            User user = apiUsers.get(credentials.getUsername());
            if (user.checkPassword(credentials.getPassword())) return Optional.of(user);
        }
        // check credentials for users stored in the database:
        try {
            User user = Users.readUser(credentials.getUsername());
            if (user != null && user.checkPassword(credentials.getPassword()) && user.isActiveUser())
                return Optional.of(user);
        } catch (Exception e) {
            LOG.debug("Authentication Failure: {} failed password check", credentials.getUsername());
            throw new AuthenticationException(e);
        }
        LOG.debug("Authentication Failure: {} not found", credentials.getUsername());
        return Optional.empty();
    }
}
