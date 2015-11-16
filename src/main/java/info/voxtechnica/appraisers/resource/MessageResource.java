package info.voxtechnica.appraisers.resource;

import com.codahale.metrics.annotation.Timed;
import com.wordnik.swagger.annotations.*;
import info.voxtechnica.appraisers.db.dao.Events;
import info.voxtechnica.appraisers.db.dao.Messages;
import info.voxtechnica.appraisers.model.Event;
import info.voxtechnica.appraisers.model.Message;
import info.voxtechnica.appraisers.model.User;
import io.dropwizard.auth.Auth;
import io.dropwizard.jersey.caching.CacheControl;
import io.dropwizard.jersey.params.BooleanParam;
import io.dropwizard.jersey.params.IntParam;
import org.apache.commons.lang3.exception.ExceptionUtils;

import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;
import java.util.SortedSet;

@Path("/v1/messages")
@Api(value = "/v1/messages", description = "Messages")
public class MessageResource {
    @Context
    private UriInfo uriInfo;

    @RolesAllowed("admin")
    @POST
    @Timed
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create and Send Message", response = Message.class)
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Created"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 406, message = "Not Acceptable")})
    public Response createMessage(@Auth User apiUser, @Valid Message message) {
        try {
            if (message.getId() != null) throw new WebApplicationException(Response.Status.NOT_ACCEPTABLE);
            Message newMessage = Messages.createMessage(message);
            URI uri = uriInfo.getAbsolutePathBuilder().path(newMessage.getId()).build();
            Events.info(apiUser.getId(), newMessage.getId(), uri, Event.HttpMethod.POST,
                    String.format("Created new Message %s", newMessage.getId()));
            return Response.created(uri).entity(newMessage).build();
        } catch (WebApplicationException e) {
            throw e; // rethrow web application exceptions and log the rest
        } catch (Exception e) {
            Events.error(apiUser.getId(), uriInfo.getRequestUri(), Event.HttpMethod.POST, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @RolesAllowed("admin")
    @GET
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    @CacheControl(noCache = true, noStore = true, mustRevalidate = true, maxAge = 0)
    @ApiOperation(value = "List Messages", response = Message.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 401, message = "Unauthorized")})
    public SortedSet<Message> readMessages(
            @Auth User apiUser,
            @ApiParam(value = "Email Address", required = false) @QueryParam("email") String email,
            @ApiParam(value = "Number of Messages", required = false) @QueryParam("limit") @DefaultValue("100") IntParam limit,
            @ApiParam(value = "Offset (last ID received)", required = false) @QueryParam("offset") String offset) {
        try {
            String emailAddress = apiUser.isAdmin() && email != null ? email : apiUser.getEmail();
            SortedSet<Message> messages;
            if (emailAddress != null && (apiUser.isAdmin() || emailAddress.equalsIgnoreCase(apiUser.getEmail()))) {
                messages = Messages.readMessagesByEmail(emailAddress, limit.get(), offset);
                Events.info(apiUser.getId(), uriInfo.getRequestUri(), Event.HttpMethod.GET,
                        String.format("Listed %d Messages for Email %s Limit %d Offset %s", messages.size(), emailAddress, limit.get(), offset));
            } else {
                messages = Messages.readMessages(limit.get());
                Events.info(apiUser.getId(), uriInfo.getRequestUri(), Event.HttpMethod.GET,
                        String.format("Listed %d Messages Limit %d", messages.size(), limit.get()));
            }
            return messages;
        } catch (WebApplicationException e) {
            throw e; // rethrow web application exceptions and log the rest
        } catch (Exception e) {
            Events.error(apiUser.getId(), uriInfo.getRequestUri(), Event.HttpMethod.GET, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @RolesAllowed("admin")
    @Path("/{id}")
    @GET
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    @CacheControl(noCache = true, noStore = true, mustRevalidate = true, maxAge = 0)
    @ApiOperation(value = "Get Message", response = Message.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 404, message = "Not Found")})
    public Message readMessage(@Auth User apiUser, @ApiParam(value = "Message ID", required = true) @PathParam("id") final String id) {
        try {
            Message message = Messages.readMessage(id);
            if (message == null) throw new WebApplicationException(Response.Status.NOT_FOUND);
            if (!message.isParticipant(apiUser.getEmail()) && !apiUser.isAdmin())
                throw new WebApplicationException(Response.Status.UNAUTHORIZED);
            Events.info(apiUser.getId(), id, uriInfo.getRequestUri(), Event.HttpMethod.GET, String.format("Read Message %s", id));
            return message;
        } catch (WebApplicationException e) {
            throw e; // rethrow web application exceptions and log the rest
        } catch (Exception e) {
            Events.error(apiUser.getId(), id, uriInfo.getRequestUri(), Event.HttpMethod.GET, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @RolesAllowed("admin")
    @Path("/{id}/versions")
    @GET
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    @CacheControl(noCache = true, noStore = true, mustRevalidate = true, maxAge = 0)
    @ApiOperation(value = "Get Message revision history", response = Message.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 401, message = "Unauthorized")})
    public List<Message> readMessageVersions(@Auth User apiUser, @ApiParam(value = "Message ID", required = true) @PathParam("id") final String id) {
        try {
            List<Message> versions = Messages.readMessageVersions(id);
            if (versions == null || versions.isEmpty()) throw new WebApplicationException(Response.Status.NOT_FOUND);
            if (!versions.get(versions.size() - 1).isParticipant(apiUser.getEmail()) && !apiUser.isAdmin())
                throw new WebApplicationException(Response.Status.UNAUTHORIZED);
            Events.info(apiUser.getId(), id, uriInfo.getRequestUri(), Event.HttpMethod.GET,
                    String.format("Listed Message %s (%d versions)", id, versions.size()));
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
    @ApiOperation(value = "Get Message version", response = Message.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 404, message = "Not Found")})
    public Message readMessageVersion(
            @Auth User apiUser,
            @ApiParam(value = "Message ID", required = true) @PathParam("id") final String id,
            @ApiParam(value = "Update ID", required = true) @PathParam("update_id") final String updateId) {
        try {
            Message message = Messages.readMessageVersion(id, updateId);
            if (message == null) throw new WebApplicationException(Response.Status.NOT_FOUND);
            if (!message.isParticipant(apiUser.getEmail()) && !apiUser.isAdmin())
                throw new WebApplicationException(Response.Status.UNAUTHORIZED);
            Events.info(apiUser.getId(), id, uriInfo.getRequestUri(), Event.HttpMethod.GET,
                    String.format("Read Message %s Update %s", id, updateId));
            return message;
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
    @ApiOperation(value = "Update Message", response = Message.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 406, message = "Not Acceptable")})
    public Response updateMessage(
            @Auth User apiUser,
            @ApiParam(value = "Message ID", required = true) @PathParam("id") final String id,
            @ApiParam(value = "Resend", required = false) @QueryParam("resend") @DefaultValue("false") final BooleanParam resend,
            @Valid Message message) {
        try {
            // Verify that the id on the path and in the object are the same
            if (message.getId() != null && !message.getId().equals(id))
                throw new WebApplicationException(Response.Status.NOT_ACCEPTABLE);
            // Fetch old version to verify it exists
            Message oldMessage = Messages.readMessage(id);
            if (oldMessage == null) throw new WebApplicationException(Response.Status.NOT_FOUND);
            // Create an updated version
            Message newMessage = Messages.updateMessage(message, resend.get());
            Events.info(apiUser.getId(), newMessage.getId(), uriInfo.getRequestUri(), Event.HttpMethod.PUT,
                    String.format("Updated Message %s Update %s", newMessage.getId(), newMessage.getUpdateId()));
            return Response.ok().entity(newMessage).build();
        } catch (WebApplicationException e) {
            throw e; // rethrow web application exceptions and log the rest
        } catch (Exception e) {
            Events.error(apiUser.getId(), id, uriInfo.getRequestUri(), Event.HttpMethod.PUT, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new WebApplicationException(Response.Status.NOT_ACCEPTABLE);
        }
    }

    @RolesAllowed("admin")
    @Path("/{id}")
    @DELETE
    @Timed
    @ApiOperation(value = "Delete Message")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "No Content"), @ApiResponse(code = 401, message = "Unauthorized")})
    public Response deleteMessage(@Auth User apiUser, @ApiParam(value = "Message ID", required = true) @PathParam("id") final String id) {
        try {
            Messages.deleteMessage(id);
            Events.info(apiUser.getId(), id, uriInfo.getRequestUri(), Event.HttpMethod.DELETE, String.format("Deleted Message %s", id));
            return Response.noContent().build();
        } catch (WebApplicationException e) {
            throw e; // rethrow web application exceptions and log the rest
        } catch (Exception e) {
            Events.error(apiUser.getId(), id, uriInfo.getRequestUri(), Event.HttpMethod.DELETE, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

}
