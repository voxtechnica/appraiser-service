package info.voxtechnica.appraisers.resource;

import com.codahale.metrics.annotation.Timed;
import com.wordnik.swagger.annotations.*;
import info.voxtechnica.appraisers.db.dao.Events;
import info.voxtechnica.appraisers.db.dao.Nodes;
import info.voxtechnica.appraisers.model.Event;
import info.voxtechnica.appraisers.model.Node;
import info.voxtechnica.appraisers.model.User;
import io.dropwizard.auth.Auth;
import io.dropwizard.jersey.caching.CacheControl;
import io.dropwizard.jersey.params.IntParam;
import org.apache.commons.lang3.exception.ExceptionUtils;

import javax.annotation.security.RolesAllowed;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.List;
import java.util.SortedSet;

@Path("/v1/nodes")
@Api(value = "/v1/nodes", description = "Application Nodes")
public class NodeResource {
    @Context
    private UriInfo uriInfo;

    @RolesAllowed("admin")
    @GET
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    @CacheControl(noCache = true, noStore = true, mustRevalidate = true, maxAge = 0)
    @ApiOperation(value = "List Application Nodes", response = Node.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 401, message = "Unauthorized")})
    public SortedSet<Node> readNodes(@Auth User apiUser) {
        try {
            SortedSet<Node> nodes = Nodes.readNodes();
            Events.info(apiUser.getId(), uriInfo.getRequestUri(), Event.HttpMethod.GET,
                    String.format("Listed %d Nodes", nodes.size()));
            return nodes;
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
    @ApiOperation(value = "Get Application Node", response = Node.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 404, message = "Not Found")})
    public Node readNode(@Auth User apiUser, @ApiParam(value = "Node ID", required = true) @PathParam("id") @Max(127) @Min(-127) @NotNull final IntParam id) {
        try {
            Node node = Nodes.readNode((byte) (int) id.get());
            if (node == null) throw new WebApplicationException(Response.Status.NOT_FOUND);
            Events.info(apiUser.getId(), uriInfo.getRequestUri(), Event.HttpMethod.GET, String.format("Read Node %d", id.get()));
            return node;
        } catch (WebApplicationException e) {
            throw e; // rethrow web application exceptions and log the rest
        } catch (Exception e) {
            Events.error(apiUser.getId(), uriInfo.getRequestUri(), Event.HttpMethod.GET, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @RolesAllowed("admin")
    @Path("/{id}/updates")
    @GET
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    @CacheControl(noCache = true, noStore = true, mustRevalidate = true, maxAge = 0)
    @ApiOperation(value = "Get Application Node History", response = Node.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 401, message = "Unauthorized")})
    public List<Node> readNodeHistory(@Auth User apiUser, @ApiParam(value = "Node ID", required = true) @PathParam("id") @Max(127) @Min(-127) @NotNull final IntParam id) {
        try {
            List<Node> list = Nodes.readNodeHistory((byte) (int) id.get());
            Events.info(apiUser.getId(), uriInfo.getRequestUri(), Event.HttpMethod.GET, String.format("Read Node %d History: %d updates", id.get(), list.size()));
            return list;
        } catch (WebApplicationException e) {
            throw e; // rethrow web application exceptions and log the rest
        } catch (Exception e) {
            Events.error(apiUser.getId(), uriInfo.getRequestUri(), Event.HttpMethod.GET, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @RolesAllowed("admin")
    @Path("/{id}")
    @DELETE
    @Timed
    @ApiOperation(value = "Delete Node ID Reservation")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "No Content"), @ApiResponse(code = 401, message = "Unauthorized")})
    public Response deleteAd(@Auth User apiUser, @ApiParam(value = "Node ID", required = true) @PathParam("id") @Max(127) @Min(-127) @NotNull final IntParam id) {
        try {
            Nodes.deleteNode((byte) (int) id.get());
            Events.info(apiUser.getId(), uriInfo.getRequestUri(), Event.HttpMethod.DELETE, String.format("Deleted Node %d Reservation History", id.get()));
            return Response.noContent().build();
        } catch (WebApplicationException e) {
            throw e; // rethrow web application exceptions and log the rest
        } catch (Exception e) {
            Events.error(apiUser.getId(), uriInfo.getRequestUri(), Event.HttpMethod.DELETE, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }
}
