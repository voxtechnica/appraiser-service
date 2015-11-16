package info.voxtechnica.appraisers.resource;

import com.codahale.metrics.annotation.Timed;
import com.wordnik.swagger.annotations.*;
import info.voxtechnica.appraisers.db.dao.Events;
import info.voxtechnica.appraisers.db.dao.Tokens;
import info.voxtechnica.appraisers.db.dao.Users;
import info.voxtechnica.appraisers.model.Event;
import info.voxtechnica.appraisers.model.Message;
import info.voxtechnica.appraisers.model.Token;
import info.voxtechnica.appraisers.model.User;
import info.voxtechnica.appraisers.util.TuidFactory;
import io.dropwizard.auth.Auth;
import io.dropwizard.jersey.caching.CacheControl;
import io.dropwizard.jersey.params.IntParam;
import org.apache.commons.lang3.exception.ExceptionUtils;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;
import java.util.SortedSet;

@Path("/v1/users")
@Api(value = "/v1/users", description = "User Accounts")
public class UserResource {
    @Context
    private UriInfo uriInfo;

    @RolesAllowed("admin")
    @POST
    @Timed
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create User", response = User.class)
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Created"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 406, message = "Not Acceptable")})
    public Response createUser(@Auth User apiUser, @Valid User newUser) {
        try {
            // New users must not have an id and they must have an email address
            if (newUser.getId() != null || newUser.getEmail() == null)
                throw new WebApplicationException(Response.Status.NOT_ACCEPTABLE);
            // Create the new User
            newUser.setId(TuidFactory.getId());
            if (newUser.getPassword() == null) newUser.setPassword(TuidFactory.getId());
            newUser.hashPassword();
            newUser = Users.createUser(newUser);
            URI uri = uriInfo.getAbsolutePathBuilder().path(newUser.getId()).build();
            Events.info(apiUser.getId(), newUser.getId(), uri, Event.HttpMethod.POST, String.format("Created new User %s", newUser.getId()));
            return Response.created(uri).entity(newUser).build();
        } catch (WebApplicationException e) {
            throw e; // rethrow web application exceptions and log the rest
        } catch (Exception e) {
            Events.error(apiUser.getId(), uriInfo.getRequestUri(), Event.HttpMethod.POST, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new WebApplicationException(Response.Status.NOT_ACCEPTABLE);
        }
    }

    @RolesAllowed("admin")
    @GET
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    @CacheControl(noCache = true, noStore = true, mustRevalidate = true, maxAge = 0)
    @ApiOperation(value = "List Users", response = User.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 401, message = "Unauthorized")})
    public SortedSet<User> readUsers(
            @Auth User apiUser,
            @ApiParam(value = "Number of Users", required = false) @QueryParam("limit") @DefaultValue("100") IntParam limit,
            @ApiParam(value = "Offset (last ID received)", required = false) @QueryParam("offset") String offset) {
        try {
            SortedSet<User> users = Users.readUsers(limit.get());
            Events.info(apiUser.getId(), uriInfo.getRequestUri(), Event.HttpMethod.GET, String.format("Listed %d Users Limit %d", users.size(), limit.get()));
            return users;
        } catch (WebApplicationException e) {
            throw e; // rethrow web application exceptions and log the rest
        } catch (Exception e) {
            Events.error(apiUser.getId(), uriInfo.getRequestUri(), Event.HttpMethod.GET, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @PermitAll
    @Path("/{id}")
    @GET
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    @CacheControl(noCache = true, noStore = true, mustRevalidate = true, maxAge = 0)
    @ApiOperation(value = "Get User", response = User.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 404, message = "Not Found")})
    public User readUser(
            @Auth User apiUser,
            @ApiParam(value = "User ID or email", required = true) @PathParam("id") @NotNull final String id) {
        try {
            if (id.equals(apiUser.getId()) || id.equals(apiUser.getEmail())) {
                Events.info(apiUser.getId(), apiUser.getId(), uriInfo.getRequestUri(), Event.HttpMethod.GET, String.format("Read API User %s", apiUser.getId()));
                return apiUser;
            }
            if (!apiUser.isAdmin()) throw new WebApplicationException(Response.Status.UNAUTHORIZED);
            User user = Users.readUser(id);
            if (user == null) throw new WebApplicationException(Response.Status.NOT_FOUND);
            Events.info(apiUser.getId(), user.getId(), uriInfo.getRequestUri(), Event.HttpMethod.GET,
                    String.format("Read User %s", user.getId()));
            return user;
        } catch (WebApplicationException e) {
            throw e; // rethrow web application exceptions and log the rest
        } catch (Exception e) {
            Events.error(apiUser.getId(), id, uriInfo.getRequestUri(), Event.HttpMethod.GET, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @Path("/{id}/exists")
    @GET
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    @CacheControl(noCache = true, noStore = true, mustRevalidate = true, maxAge = 0)
    @ApiOperation(value = "Get User ID from Email", response = String.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 404, message = "Not Found")})
    public Response readUser(@ApiParam(value = "Email Address", required = true) @PathParam("id") final String id) {
        String userId = null;
        try {
            userId = Users.readUserId(id);
            if (userId == null) throw new WebApplicationException(Response.Status.NOT_FOUND);
            Events.info(userId, uriInfo.getRequestUri(), Event.HttpMethod.GET, String.format("Read User ID %s for Email %s", userId, id));
            return Response.ok("{\"id\":\"" + userId + "\"}").build();
        } catch (WebApplicationException e) {
            throw e; // rethrow web application exceptions and log the rest
        } catch (Exception e) {
            Events.error(userId == null ? null : userId, uriInfo.getRequestUri(), Event.HttpMethod.GET, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @Path("/{id}/resets")
    @POST
    @Timed
    @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Send Password Reset Token", response = String.class)
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Created"), @ApiResponse(code = 406, message = "Not Acceptable")})
    public Response sendResetToken(@ApiParam(value = "Email Address", required = true) @PathParam("id") final String id) {
        try {
            // Send reset token to this user (creating new account if necessary)
            Message message = Users.sendResetToken(id);
            if (message == null) throw new WebApplicationException(Response.Status.NOT_ACCEPTABLE);
            Events.info(null, message.getId(), uriInfo.getRequestUri(), Event.HttpMethod.POST,
                    String.format("Sent Reset Token to User %s with Status %s", id, message.getStatus()));
            return Response.ok("{\"status\":\"" + message.getStatus() + "\"}").build();
        } catch (WebApplicationException e) {
            throw e; // rethrow web application exceptions and log the rest
        } catch (Exception e) {
            Events.error(null, uriInfo.getRequestUri(), Event.HttpMethod.POST, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @Path("/{id}/resets/{token}")
    @PUT
    @Timed
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Reset Password", response = String.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 406, message = "Not Acceptable")})
    public Response resetPassword(
            @ApiParam(value = "Email Address", required = true) @PathParam("id") final String id,
            @ApiParam(value = "Reset Token", required = true) @PathParam("token") final String token,
            @Valid User password) {
        try {
            User user = Users.readUser(id);
            if (user == null || !token.equalsIgnoreCase(user.getResetToken()) || password == null)
                throw new WebApplicationException(Response.Status.NOT_ACCEPTABLE);
            user.setPassword(password.getPassword());
            user.hashPassword();
            user.setResetToken(null);
            Users.updateUser(user);
            Events.info(null, uriInfo.getRequestUri(), Event.HttpMethod.POST, String.format("Reset Password for User %s", id));
            return Response.ok("{\"status\":\"RESET\"}").build();
        } catch (WebApplicationException e) {
            throw e; // rethrow web application exceptions and log the rest
        } catch (Exception e) {
            Events.error(null, uriInfo.getRequestUri(), Event.HttpMethod.PUT, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @RolesAllowed("admin")
    @Path("/{id}/versions")
    @GET
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    @CacheControl(noCache = true, noStore = true, mustRevalidate = true, maxAge = 0)
    @ApiOperation(value = "Get User revision history", response = User.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 401, message = "Unauthorized")})
    public List<User> readUserVersions(
            @Auth User apiUser,
            @ApiParam(value = "User ID or email", required = true) @PathParam("id") final String id) {
        try {
            List<User> versions = Users.readUserVersions(id);
            String userId = versions.size() > 0 ? versions.get(0).getId() : null;
            Events.info(apiUser.getId(), userId, uriInfo.getRequestUri(), Event.HttpMethod.GET,
                    String.format("Listed User %s (%d versions)", userId, versions.size()));
            return versions;
        } catch (WebApplicationException e) {
            throw e; // rethrow web application exceptions and log the rest
        } catch (Exception e) {
            Events.error(apiUser.getId(), id, uriInfo.getRequestUri(), Event.HttpMethod.GET, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @RolesAllowed("admin")
    @Path("/{id}/versions/{update_id}")
    @GET
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    @CacheControl(immutable = true)
    @ApiOperation(value = "Get User version", response = User.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 404, message = "Not Found")})
    public User readUserVersion(
            @Auth User apiUser,
            @ApiParam(value = "User ID", required = true) @PathParam("id") final String id,
            @ApiParam(value = "Update ID", required = true) @PathParam("update_id") final String updateId) {
        try {
            User user = Users.readUserVersion(id, updateId);
            if (user == null) throw new WebApplicationException(Response.Status.NOT_FOUND);
            Events.info(apiUser.getId(), id, uriInfo.getRequestUri(), Event.HttpMethod.GET,
                    String.format("Read User %s Update %s", id, updateId));
            return user;
        } catch (WebApplicationException e) {
            throw e; // rethrow web application exceptions and log the rest
        } catch (Exception e) {
            Events.error(apiUser.getId(), id, uriInfo.getRequestUri(), Event.HttpMethod.GET, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @RolesAllowed("admin")
    @Path("/{id}/tokens")
    @GET
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    @CacheControl(noCache = true, noStore = true, mustRevalidate = true, maxAge = 0)
    @ApiOperation(value = "Get User OAuth Tokens", response = Token.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 404, message = "Not Found")})
    public List<Token> readUserTokens(
            @Auth User apiUser,
            @ApiParam(value = "User ID or email", required = true) @PathParam("id") final String id,
            @ApiParam(value = "Number of Tokens", required = false) @QueryParam("limit") IntParam limit) {
        try {
            User user = Users.readUser(id);
            if (user == null) throw new WebApplicationException(Response.Status.NOT_FOUND);
            List<Token> tokens = Tokens.readUserTokens(user.getId(), limit == null ? null : limit.get());
            Events.info(apiUser.getId(), user.getId(), uriInfo.getRequestUri(), Event.HttpMethod.GET,
                    String.format("Listed %d Tokens for User %s", tokens.size(), user.getId()));
            return tokens;
        } catch (WebApplicationException e) {
            throw e; // rethrow web application exceptions and log the rest
        } catch (Exception e) {
            Events.error(apiUser.getId(), id, uriInfo.getRequestUri(), Event.HttpMethod.GET, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @RolesAllowed("admin")
    @Path("/{id}")
    @PUT
    @Timed
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update User", response = User.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 406, message = "Not Acceptable")})
    public Response updateUser(@Auth User apiUser,
                               @ApiParam(value = "User ID", required = true) @PathParam("id") final String id, @Valid User user) {
        try {
            // TODO: enable a user to update just their password, as an authenticated user
            // Verify that the id on the path and in the object are the same
            if (user.getId() != null && !user.getId().equals(id))
                throw new WebApplicationException(Response.Status.NOT_ACCEPTABLE);
            // Check for an updated password
            if (user.getPassword() != null && !user.getPassword().isEmpty()) user.hashPassword();
            // Preserve the previous password hash, if not supplied or updated.
            if (user.getPasswordHash() == null) {
                User oldVersion = Users.readUser(id);
                if (oldVersion != null) user.setPasswordHash(oldVersion.getPasswordHash());
            }
            // Create an updated user version
            User newVersion = Users.updateUser(user);
            Events.info(apiUser.getId(), newVersion.getId(), uriInfo.getRequestUri(), Event.HttpMethod.PUT,
                    String.format("Updated User %s Update %s", newVersion.getId(), newVersion.getUpdateId()));
            return Response.ok().entity(newVersion).build();
        } catch (WebApplicationException e) {
            throw e; // rethrow web application exceptions and log the rest
        } catch (Exception e) {
            Events.error(apiUser.getId(), id, uriInfo.getRequestUri(), Event.HttpMethod.PUT, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new WebApplicationException(Response.Status.NOT_ACCEPTABLE);
        }
    }

    @PermitAll
    @Path("/{id}")
    @DELETE
    @Timed
    @ApiOperation(value = "Delete User")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "No Content"), @ApiResponse(code = 401, message = "Unauthorized")})
    public Response deleteUser(
            @Auth User apiUser,
            @ApiParam(value = "User ID", required = true) @PathParam("id") @NotNull final String id) {
        try {
            if (!apiUser.isAdmin() && !id.equals(apiUser.getId()) && !id.equals(apiUser.getEmail()))
                throw new WebApplicationException(Response.Status.UNAUTHORIZED);
            Users.deleteUser(id);
            Events.info(apiUser.getId(), id, uriInfo.getRequestUri(), Event.HttpMethod.DELETE, String.format("Deleted User %s", id));
            return Response.noContent().build();
        } catch (WebApplicationException e) {
            throw e; // rethrow web application exceptions and log the rest
        } catch (Exception e) {
            Events.error(apiUser.getId(), id, uriInfo.getRequestUri(), Event.HttpMethod.DELETE, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

}
