package info.voxtechnica.appraisers.resource;

import com.codahale.metrics.annotation.Timed;
import com.wordnik.swagger.annotations.*;
import info.voxtechnica.appraisers.db.dao.Events;
import info.voxtechnica.appraisers.db.dao.Metrics;
import info.voxtechnica.appraisers.model.Event;
import info.voxtechnica.appraisers.model.MetricCount;
import info.voxtechnica.appraisers.model.MetricStat;
import info.voxtechnica.appraisers.model.User;
import io.dropwizard.auth.Auth;
import io.dropwizard.jersey.caching.CacheControl;
import io.dropwizard.jersey.params.IntParam;
import org.apache.commons.lang3.exception.ExceptionUtils;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.List;

@Path("/v1/metric_tags")
@Api(value = "/v1/metric_tags", description = "Metric Tags")
public class MetricTagResource {
    @Context
    private UriInfo uriInfo;

    @RolesAllowed("admin")
    @GET
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    @CacheControl(noCache = true, noStore = true, mustRevalidate = true, maxAge = 0)
    @ApiOperation(value = "List Metric Tags, Counts, and Average Durations", response = MetricCount.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 401, message = "Unauthorized")})
    public List<MetricCount> readMetricTags(@Auth User apiUser) {
        try {
            List<MetricCount> counts = Metrics.readMetricCountsByTag();
            Events.info(apiUser.getId(), uriInfo.getRequestUri(), Event.HttpMethod.GET, String.format("Read %d Metric Tags", counts.size()));
            return counts;
        } catch (WebApplicationException e) {
            throw e; // rethrow web application exceptions and log the rest
        } catch (Exception e) {
            Events.error(apiUser.getId(), uriInfo.getRequestUri(), Event.HttpMethod.GET, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @RolesAllowed("admin")
    @Path("/{tag}/stats")
    @GET
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    @CacheControl(immutable = true)
    @ApiOperation(value = "Read Metric Tag Stats", response = MetricStat.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 401, message = "Unauthorized")})
    public MetricStat readMetricTagStats(
            @Auth User apiUser,
            @ApiParam(value = "Metric Tag", required = true) @PathParam("tag") final String tag,
            @ApiParam(value = "Recent Metrics", required = false) @QueryParam("seconds") IntParam seconds,
            @ApiParam(value = "Number of Metrics", required = false) @QueryParam("limit") @DefaultValue("100000") IntParam limit,
            @ApiParam(value = "Last Metric ID", required = false) @QueryParam("offset") String offset) {
        try {
            MetricStat stat;
            if (seconds != null) stat = Metrics.readRecentMetricDurationStatsByTag(tag, seconds.get());
            else stat = Metrics.readMetricDurationStatsByTag(tag, limit.get(), offset);
            if (stat.getCount() == 0) throw new WebApplicationException(Response.Status.NOT_FOUND);
            Events.info(apiUser.getId(), uriInfo.getRequestUri(), Event.HttpMethod.GET, String.format("Read %d Metric Durations for Tag %s", stat.getCount(), tag));
            return stat;
        } catch (WebApplicationException e) {
            throw e; // rethrow web application exceptions and log the rest
        } catch (Exception e) {
            Events.error(apiUser.getId(), uriInfo.getRequestUri(), Event.HttpMethod.GET, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }
}
