package info.voxtechnica.appraisers.resource;

import com.codahale.metrics.annotation.Timed;
import com.wordnik.swagger.annotations.*;
import info.voxtechnica.appraisers.db.dao.Events;
import info.voxtechnica.appraisers.model.Event;
import info.voxtechnica.appraisers.model.User;
import io.dropwizard.auth.Auth;
import io.dropwizard.jersey.caching.CacheControl;
import io.dropwizard.jersey.params.IntParam;
import org.apache.commons.lang3.exception.ExceptionUtils;

import javax.annotation.security.PermitAll;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.Map;

@Path("/v1/event_counts")
@Api(value = "/v1/event_counts", description = "Daily Event Counts")
public class EventCountResource {
    @Context
    private UriInfo uriInfo;

    @PermitAll
    @GET
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    @CacheControl(noCache = true, noStore = true, mustRevalidate = true, maxAge = 0)
    @ApiOperation(value = "List Daily Event Counts")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 401, message = "Unauthorized")})
    public Map<Integer, Long> readEventCounts(
            @Auth User apiUser,
            @ApiParam(value = "User ID", required = false) @QueryParam("user_id") String userId,
            @ApiParam(value = "Log Level", required = false) @QueryParam("log_level") Event.LogLevel logLevel,
            @ApiParam(value = "Number of Days", required = false) @QueryParam("limit") @DefaultValue("1000") IntParam limit,
            @ApiParam(value = "Last Day YYYYMMDD", required = false) @QueryParam("offset") IntParam offset) {
        try {
            Map<Integer, Long> counts = null;
            // Administrators can query anything
            if (apiUser.isAdmin()) {
                if (userId != null) {
                    counts = Events.readEventCountsByUserDay(userId, limit.get(), offset == null ? null : offset.get());
                    Events.info(apiUser.getId(), uriInfo.getRequestUri(), Event.HttpMethod.GET,
                            String.format("Read %d Event Counts for User %s", counts.size(), userId));
                } else if (logLevel != null) {
                    counts = Events.readEventCountsByLevelDay(logLevel, limit.get(), offset == null ? null : offset.get());
                    Events.info(apiUser.getId(), uriInfo.getRequestUri(), Event.HttpMethod.GET,
                            String.format("Read %d Event Counts for LogLevel %s", counts.size(), logLevel));
                }
            }
            // Regular users can only see their own event log
            if (counts == null) {
                counts = Events.readEventCountsByUserDay(apiUser.getId(), limit.get(), offset == null ? null : offset.get());
                Events.info(apiUser.getId(), uriInfo.getRequestUri(), Event.HttpMethod.GET,
                        String.format("Read %d Event Counts for User %s", counts.size(), apiUser.getId()));
            }
            return counts;
        } catch (WebApplicationException e) {
            throw e; // rethrow web application exceptions and log the rest
        } catch (Exception e) {
            Events.error(apiUser.getId(), uriInfo.getRequestUri(), Event.HttpMethod.GET, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }
}
