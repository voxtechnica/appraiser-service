package info.voxtechnica.appraisers.resource;

import com.codahale.metrics.annotation.Timed;
import com.wordnik.swagger.annotations.*;
import info.voxtechnica.appraisers.db.dao.Events;
import info.voxtechnica.appraisers.db.dao.Metrics;
import info.voxtechnica.appraisers.model.Event;
import info.voxtechnica.appraisers.model.Metric;
import info.voxtechnica.appraisers.model.User;
import io.dropwizard.auth.Auth;
import io.dropwizard.jersey.caching.CacheControl;
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
import java.util.SortedSet;

@Path("/v1/metrics")
@Api(value = "/v1/metrics", description = "Metrics")
public class MetricResource {
    @Context
    private UriInfo uriInfo;

    @RolesAllowed("admin")
    @POST
    @Timed
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create Metric", response = Metric.class)
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Created"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 406, message = "Not Acceptable")})
    public Response createMetric(@Auth User apiUser, @Valid Metric metric) {
        try {
            if (metric.getId() != null) throw new WebApplicationException(Response.Status.NOT_ACCEPTABLE);
            metric = Metrics.createMetric(metric);
            if (metric == null) throw new WebApplicationException(Response.Status.NOT_ACCEPTABLE);
            URI uri = uriInfo.getAbsolutePathBuilder().path(metric.getId()).build();
            Events.info(apiUser.getId(), metric.getId(), uri, Event.HttpMethod.GET, String.format("Created Metric %s", metric.getId()));
            return Response.created(uri).entity(metric).build();
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
    @ApiOperation(value = "List Metrics", response = Metric.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 401, message = "Unauthorized")})
    public SortedSet<Metric> readMetrics(
            @Auth User apiUser,
            @ApiParam(value = "Entity ID", required = false) @QueryParam("entity_id") String entityId,
            @ApiParam(value = "Tag", required = false) @QueryParam("tag") String tag,
            @ApiParam(value = "Number of Metrics", required = false) @QueryParam("limit") @DefaultValue("1000") IntParam limit,
            @ApiParam(value = "Last Metric ID", required = false) @QueryParam("offset") String offset) {
        try {
            if (entityId == null && tag == null) throw new WebApplicationException(Response.Status.NOT_ACCEPTABLE);
            SortedSet<Metric> metrics;
            if (entityId != null) metrics = Metrics.readMetricsByEntity(entityId, limit.get(), offset);
            else metrics = Metrics.readMetricsByTag(tag, limit.get(), offset);
            Events.info(apiUser.getId(), uriInfo.getRequestUri(), Event.HttpMethod.GET, String.format("Read %d Metrics", metrics.size()));
            return metrics;
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
    @CacheControl(immutable = true)
    @ApiOperation(value = "Read Metric", response = Metric.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 401, message = "Unauthorized")})
    public Metric readMetric(@Auth User apiUser, @ApiParam(value = "Metric ID", required = true) @PathParam("id") final String id) {
        try {
            Metric metric = Metrics.readMetric(id);
            if (metric == null) throw new WebApplicationException(Response.Status.NOT_FOUND);
            Events.info(apiUser.getId(), metric.getId(), uriInfo.getRequestUri(), Event.HttpMethod.GET, String.format("Read Metric %s", metric.getId()));
            return metric;
        } catch (WebApplicationException e) {
            throw e; // rethrow web application exceptions and log the rest
        } catch (Exception e) {
            Events.error(apiUser.getId(), id, uriInfo.getRequestUri(), Event.HttpMethod.GET, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @RolesAllowed("admin")
    @Path("/{id}")
    @DELETE
    @Timed
    @ApiOperation(value = "Delete Metric")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "No Content"), @ApiResponse(code = 401, message = "Unauthorized")})
    public Response deleteMetric(@Auth User apiUser, @ApiParam(value = "Metric ID", required = true) @PathParam("id") final String id) {
        try {
            Metric metric = Metrics.readMetric(id);
            if (metric == null) throw new WebApplicationException(Response.Status.NOT_FOUND);
            Metrics.deleteMetric(id);
            Events.info(apiUser.getId(), id, uriInfo.getRequestUri(), Event.HttpMethod.DELETE, String.format("Deleted Metric %s", id));
            return Response.noContent().build();
        } catch (WebApplicationException e) {
            throw e; // rethrow web application exceptions and log the rest
        } catch (Exception e) {
            Events.error(apiUser.getId(), id, uriInfo.getRequestUri(), Event.HttpMethod.DELETE, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }
}
