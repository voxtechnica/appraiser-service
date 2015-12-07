package info.voxtechnica.appraisers.resource;

import com.codahale.metrics.annotation.Timed;
import com.wordnik.swagger.annotations.*;
import info.voxtechnica.appraisers.db.dao.Events;
import info.voxtechnica.appraisers.model.Event;
import info.voxtechnica.appraisers.model.Tuid;
import info.voxtechnica.appraisers.model.User;
import io.dropwizard.auth.Auth;
import io.dropwizard.jersey.caching.CacheControl;
import io.dropwizard.jersey.params.IntParam;
import org.apache.commons.lang3.exception.ExceptionUtils;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.SortedSet;

@Path("/v1/events")
@Api(value = "/v1/events", description = "Events")
public class EventResource {
    @Context
    private UriInfo uriInfo;

    @RolesAllowed("admin")
    @POST
    @Timed
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create Event", response = Event.class)
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Created"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 406, message = "Not Acceptable")})
    public Response createEvent(@Auth User apiUser, @Valid Event event) {
        try {
            // Event ID must be created internally
            if (event.getId() != null) throw new WebApplicationException(Response.Status.NOT_ACCEPTABLE);
            // Users cannot associate events with other users
            event.setUserId(apiUser.getId());
            // No need to log the event of creating the event :)
            Event created = Events.createEvent(event);
            if (created == null) throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
            URI uri = uriInfo.getAbsolutePathBuilder().path(created.getId()).build();
            return Response.created(uri).entity(created).build();
        } catch (WebApplicationException e) {
            throw e; // rethrow web application exceptions and log the rest
        } catch (Exception e) {
            Events.error(apiUser.getId(), uriInfo.getRequestUri(), Event.HttpMethod.POST, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @PermitAll
    @GET
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    @CacheControl(noCache = true, noStore = true, mustRevalidate = true, maxAge = 0)
    @ApiOperation(value = "List Events", response = Event.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 401, message = "Unauthorized")})
    public SortedSet<Event> readEvents(
            @Auth User apiUser,
            @ApiParam(value = "Recent Events", required = false) @QueryParam("seconds") IntParam seconds,
            @ApiParam(value = "Entity ID", required = false) @QueryParam("entity_id") String entityId,
            @ApiParam(value = "User ID", required = false) @QueryParam("user_id") String userId,
            @ApiParam(value = "Log Level", required = false) @QueryParam("log_level") Event.LogLevel logLevel,
            @ApiParam(value = "Integer Date YYYYMMDD", required = false) @QueryParam("day") IntParam day,
            @ApiParam(value = "Number of Events", required = false) @QueryParam("limit") @DefaultValue("1000") IntParam limit,
            @ApiParam(value = "Last Event ID", required = false) @QueryParam("offset") String offset) {
        try {
            SortedSet<Event> events;
            // Default query date is today
            Integer queryDay = day == null ? (new Tuid().getYearMonthDay()) : day.get();
            // Administrators can query the entire event log
            if (apiUser.isAdmin()) {
                if (seconds != null) events = Events.readRecentEvents(seconds.get());
                else if (entityId != null) events = Events.readEventsByEntity(entityId, limit.get(), offset);
                else if (userId != null)
                    events = Events.readEventsByUserDay(userId, queryDay, limit.get(), offset);
                else if (logLevel != null)
                    events = Events.readEventsByLevelDay(logLevel, queryDay, limit.get(), offset);
                else events = Events.readEventsByDay(queryDay, limit.get(), offset);
            } else {
                // Regular users can only see their own event log
                events = Events.readEventsByUserDay(apiUser.getId(), queryDay, limit.get(), offset);
            }
            // Filter by log level if specified
            if (logLevel != null)
                for (Event event : events) if (!logLevel.equals(event.getLogLevel())) events.remove(event);
            Events.info(apiUser.getId(), uriInfo.getRequestUri(), Event.HttpMethod.GET, String.format("Read %d Events", events.size()));
            return events;
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
    @CacheControl(immutable = true)
    @ApiOperation(value = "Read Event", response = Event.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 401, message = "Unauthorized")})
    public Event readEvent(@Auth User apiUser, @ApiParam(value = "Event ID", required = true) @PathParam("id") final String id) {
        try {
            Event event = Events.readEvent(id);
            if (event == null) throw new WebApplicationException(Response.Status.NOT_FOUND);
            if (!apiUser.isAdmin() && !apiUser.getId().equals(event.getUserId()))
                throw new WebApplicationException(Response.Status.UNAUTHORIZED);
            Events.info(apiUser.getId(), event.getId(), uriInfo.getRequestUri(), Event.HttpMethod.GET, String.format("Read Event %s", event.getId()));
            return event;
        } catch (WebApplicationException e) {
            throw e; // rethrow web application exceptions and log the rest
        } catch (Exception e) {
            Events.error(apiUser.getId(), id, uriInfo.getRequestUri(), Event.HttpMethod.GET, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

}
