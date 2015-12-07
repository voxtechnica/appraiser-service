package info.voxtechnica.appraisers.resource;

import com.codahale.metrics.annotation.Timed;
import com.wordnik.swagger.annotations.*;
import info.voxtechnica.appraisers.db.dao.Events;
import info.voxtechnica.appraisers.db.dao.Tokens;
import info.voxtechnica.appraisers.db.dao.Users;
import info.voxtechnica.appraisers.model.*;
import io.dropwizard.auth.Auth;
import io.dropwizard.jersey.caching.CacheControl;
import io.dropwizard.jersey.params.UUIDParam;
import org.apache.commons.lang3.exception.ExceptionUtils;

import javax.annotation.security.PermitAll;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Path("/v1/tokens")
@Api(value = "/v1/tokens", description = "OAuth2 Bearer Tokens")
public class TokenResource {
    @Context
    private UriInfo uriInfo;

    /**
     * This method provides a strict interpretation of http://tools.ietf.org/html/rfc6749#section-4.3.2
     */
    @POST
    @Timed
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create OAuth2 Bearer Token", response = AccessToken.class)
    @ApiResponses(value = {
            @ApiResponse(code = 201, message = "Created"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 404, message = "Not Found"),
            @ApiResponse(code = 406, message = "Not Acceptable")})
    public Response createTokenForm(
            @ApiParam(value = "OAuth Grant Type", required = false) @FormParam("grant_type") @DefaultValue("password") String grantType,
            @ApiParam(value = "User ID or Email Address", required = true) @FormParam("username") String username,
            @ApiParam(value = "Password", required = true) @FormParam("password") String password) {
        User user = null;
        try {
            // We only support OAuth2 Resource Owner Password Credentials Grant here
            if (!grantType.equals("password")) throw new WebApplicationException(Response.Status.METHOD_NOT_ALLOWED);
            if (username == null || password == null) throw new WebApplicationException(Response.Status.NOT_ACCEPTABLE);
            // Validate User Credentials
            user = Users.readUser(username);
            if (user == null) throw new WebApplicationException(Response.Status.NOT_FOUND);
            if (User.Status.DISABLED == user.getStatus() || !user.checkPassword(password))
                throw new WebApplicationException(Response.Status.UNAUTHORIZED);
            // Create a new OAuth 2 Bearer Token
            UUID token = Tokens.createOAuthToken(user.getId());
            if (token == null) throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
            AccessToken accessToken = new AccessToken(token.toString());
            URI uri = uriInfo.getAbsolutePathBuilder().path(accessToken.getAccessToken()).build();
            Events.info(user.getId(), user.getId(), uri, Event.HttpMethod.POST,
                    String.format("Created new OAuth2 Token for User %s", user.getId()));
            return Response.created(uri).entity(accessToken).build();
        } catch (WebApplicationException e) {
            throw e; // rethrow web application exceptions and log the rest
        } catch (Exception e) {
            Events.error(user == null ? null : user.getId(), uriInfo.getRequestUri(), Event.HttpMethod.POST, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * This method provides a loose interpretation of http://tools.ietf.org/html/rfc6749#section-4.3.2
     * (1) we're using camelCase instead of snake_case for the request, and
     * (2) we're allowing Content-Type application/json instead of application/x-www-form-urlencoded
     */
    @POST
    @Timed
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create OAuth2 Bearer Token", response = AccessToken.class)
    @ApiResponses(value = {
            @ApiResponse(code = 201, message = "Created"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 404, message = "Not Found"),
            @ApiResponse(code = 406, message = "Not Acceptable")})
    public Response createTokenJson(
            @ApiParam(value = "OAuth Token Request", required = true) @Valid AccessRequest request) {
        Boolean validCredentials = false;
        User user = null;
        try {
            // Validate credentials: a user's password
            if ("password".equalsIgnoreCase(request.getGrantType())) {
                user = Users.readUser(request.getUsername());
                validCredentials = user != null && !User.Status.DISABLED.equals(user.getStatus()) && user.checkPassword(request.getPassword());
            } else throw new WebApplicationException(Response.Status.METHOD_NOT_ALLOWED);
            if (!validCredentials) throw new WebApplicationException(Response.Status.NOT_FOUND); // deliberately obscure
            // Create a new OAuth 2 Bearer Token
            UUID token = Tokens.createOAuthToken(user.getId());
            if (token == null) throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
            AccessToken accessToken = new AccessToken(token.toString());
            URI uri = uriInfo.getAbsolutePathBuilder().path(accessToken.getAccessToken()).build();
            Set<String> entityIds = new HashSet<>();
            entityIds.add(user.getId());
            Events.info(user.getId(), entityIds, uri, Event.HttpMethod.POST,
                    String.format("Created new OAuth2 Token for User %s", user.getId()));
            return Response.created(uri).entity(accessToken).build();
        } catch (WebApplicationException e) {
            throw e; // rethrow web application exceptions and log the rest
        } catch (Exception e) {
            Events.error(user == null ? null : user.getId(), uriInfo.getRequestUri(), Event.HttpMethod.POST, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * This method provides a way to check to see if a specified token is valid (security by obscurity).
     * It can be used to validate a session without triggering the backup basic authentication prompt.
     *
     * @param token OAuth2 Bearer Token (a UUID)
     * @return token metadata, including whether it's expired.
     */
    @Path("/{token}")
    @GET
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    @CacheControl(noCache = true, noStore = true, mustRevalidate = true, maxAge = 0)
    @ApiOperation(value = "Get OAuth2 Bearer Token")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 404, message = "Not Found")})
    public Token readToken(@ApiParam(value = "Token", required = true) @PathParam("token") final UUIDParam token) {
        try {
            Token oAuthToken = Tokens.readOAuthToken(token.get());
            if (oAuthToken == null) throw new WebApplicationException(Response.Status.NOT_FOUND);
            User user = Users.readUser(oAuthToken.getUserId());
            if (user != null)
                Events.info(user.getId(), uriInfo.getRequestUri(), Event.HttpMethod.GET, String.format("OAuth Token fetched for User %s", user.getId()));
            return oAuthToken;
        } catch (WebApplicationException e) {
            throw e; // rethrow web application exceptions and log the rest
        } catch (Exception e) {
            Events.error(null, uriInfo.getRequestUri(), Event.HttpMethod.GET, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @PermitAll
    @Path("/{token}")
    @DELETE
    @Timed
    @ApiOperation(value = "Delete OAuth2 Bearer Token")
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "No Content"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 404, message = "Not Found")})
    public Response deleteToken(
            @Auth User apiUser,
            @ApiParam(value = "Token", required = true) @PathParam("token") final UUIDParam token) {
        try {
            // Look up the user that corresponds to this token to authorize deletion
            Token oAuthToken = Tokens.readOAuthToken(token.get());
            if (oAuthToken == null) throw new WebApplicationException(Response.Status.NOT_FOUND);
            String userId = oAuthToken.getUserId();
            if (!apiUser.getId().equals(userId) && !apiUser.isAdmin())
                throw new WebApplicationException(Response.Status.UNAUTHORIZED);
            // Delete the token
            Tokens.deleteOAuthToken(token.get());
            Events.info(apiUser.getId(), userId, uriInfo.getRequestUri(), Event.HttpMethod.DELETE, String.format("Deleted OAuth2 Token %s for User %s", token, userId));
            return Response.noContent().build();
        } catch (WebApplicationException e) {
            throw e; // rethrow web application exceptions and log the rest
        } catch (Exception e) {
            Events.error(apiUser.getId(), uriInfo.getRequestUri(), Event.HttpMethod.DELETE, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

}
