package info.voxtechnica.appraisers.auth;

import info.voxtechnica.appraisers.model.User;
import io.dropwizard.auth.Authorizer;

/**
 * The ApiUserAuthorizer determines whether the specified User has the required Role, and therefore permission to do something.
 */
public class ApiUserAuthorizer implements Authorizer<User> {

    @Override
    public boolean authorize(User user, String role) {
        return user.hasRole(role);
    }
}
