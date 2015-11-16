package info.voxtechnica.appraisers.resource;

import com.codahale.metrics.annotation.Timed;
import com.wordnik.swagger.annotations.*;
import info.voxtechnica.appraisers.db.dao.Events;
import info.voxtechnica.appraisers.model.Event;
import info.voxtechnica.appraisers.model.Tuid;
import info.voxtechnica.appraisers.util.TuidFactory;
import info.voxtechnica.appraisers.view.TuidView;
import info.voxtechnica.appraisers.view.TuidsView;
import io.dropwizard.jersey.caching.CacheControl;
import io.dropwizard.jersey.params.IntParam;
import org.apache.commons.lang3.exception.ExceptionUtils;

import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.net.URI;
import java.util.ArrayList;

@Path("/v1/tuids")
@Api(value = "/v1/tuids", description = "Time-based Unique IDs")
public class TuidResource {
    @Context
    private UriInfo uriInfo;
    @Context
    private HttpHeaders requestHeaders;

    /**
     * Create a new TUID. The ID is auto-generated if it's not provided.
     *
     * @return the new TUID, along with a URI pointing to it
     */
    @POST
    @Timed
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create a Tuid", response = Tuid.class)
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Created")})
    public Response createTuid() {
        try {
            Tuid tuid = TuidFactory.generateTuid();
            URI uri = uriInfo.getAbsolutePathBuilder().path(tuid.getId()).build();
            Events.info(null, tuid.getId(), uri, Event.HttpMethod.POST, String.format("Created new Tuid %s", tuid.getId()));
            return Response.created(uri).entity(tuid).build();
        } catch (WebApplicationException e) {
            throw e; // rethrow web application exceptions and log the rest
        } catch (Exception e) {
            Events.error(null, uriInfo.getRequestUri(), Event.HttpMethod.POST, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Fetch a list of TUIDs
     *
     * @return List of TUIDs
     */
    @GET
    @Timed
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_HTML})
    @CacheControl(noCache = true, noStore = true, mustRevalidate = true, maxAge = 0)
    @ApiOperation(value = "Get a List of Tuids", response = Tuid.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK")})
    public Response readTuids(
            @ApiParam(value = "Comma-separated list of tuids", required = false) @QueryParam("ids") String ids,
            @ApiParam(value = "Number of Tuids to generate", required = false) @QueryParam("limit") @DefaultValue("20") IntParam limit) {
        try {
            ArrayList<Tuid> tuids = new ArrayList<>();
            Integer count = limit.get();
            if (ids != null) {
                String[] values = ids.split(",");
                count = values.length;
                for (String id : values) tuids.add(new Tuid(id));
            } else for (int i = 0; i < count; i++) tuids.add(TuidFactory.generateTuid());
            Events.info(null, uriInfo.getRequestUri(), Event.HttpMethod.GET, String.format("Read %d Tuids", tuids.size()));
            if (requestHeaders.getAcceptableMediaTypes().contains(MediaType.APPLICATION_JSON_TYPE))
                return Response.ok(tuids).build();
            else
                return Response.ok(new TuidsView(tuids)).build();
        } catch (WebApplicationException e) {
            throw e; // rethrow web application exceptions and log the rest
        } catch (Exception e) {
            Events.error(null, uriInfo.getRequestUri(), Event.HttpMethod.GET, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Fetch the specified TUID, displaying the createdAt timestamp.
     *
     * @param id
     * @return the specified TUID as JSON
     */
    @Path("/{id}")
    @GET
    @Timed
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_HTML})
    @CacheControl(immutable = true)
    @ApiOperation(value = "Get a Tuid", response = Tuid.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 404, message = "Not Found")})
    public Response readTuid(@ApiParam(value = "id", required = true) @PathParam("id") final String id) {
        try {
            Tuid tuid = new Tuid(id);
            Events.info(null, tuid.getId(), uriInfo.getRequestUri(), Event.HttpMethod.GET, String.format("Read Tuid %s", tuid.getId()));
            if (requestHeaders.getAcceptableMediaTypes().contains(MediaType.APPLICATION_JSON_TYPE))
                return Response.ok(tuid).build();
            else
                return Response.ok(new TuidView(tuid)).build();
        } catch (WebApplicationException e) {
            throw e; // rethrow web application exceptions and log the rest
        } catch (Exception e) {
            Events.error(null, uriInfo.getRequestUri(), Event.HttpMethod.GET, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }
}