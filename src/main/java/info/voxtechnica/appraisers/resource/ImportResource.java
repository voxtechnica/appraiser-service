package info.voxtechnica.appraisers.resource;

import com.codahale.metrics.annotation.Timed;
import com.wordnik.swagger.annotations.*;
import info.voxtechnica.appraisers.db.dao.Events;
import info.voxtechnica.appraisers.db.dao.Imports;
import info.voxtechnica.appraisers.model.Event;
import info.voxtechnica.appraisers.model.Import;
import info.voxtechnica.appraisers.model.Tuid;
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
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;

@Path("/v1/imports")
@Api(value = "/v1/imports", description = "Imports")
public class ImportResource {
    @Context
    private UriInfo uriInfo;

    @RolesAllowed("admin")
    @GET
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    @CacheControl(noCache = true, noStore = true, mustRevalidate = true, maxAge = 0)
    @ApiOperation(value = "List Imports", response = Import.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 401, message = "Unauthorized")})
    public SortedSet<Import> readImports(
            @Auth User apiUser,
            @ApiParam(value = "Day (YYYYMMDD)", required = false) @QueryParam("day") IntParam day,
            @ApiParam(value = "Days (YYYYMMDD, comma-separated)", required = false) @QueryParam("days") String days) {
        try {
            SortedSet<Import> imports;
            if (day != null) imports = Imports.readImports(day.get());
            else if (days != null) {
                List<Integer> dayList = new ArrayList<>();
                String[] dates = days.split(",");
                for (String date : dates) dayList.add(Integer.valueOf(date));
                imports = Imports.readImports(dayList);
            } else imports = Imports.readImports();
            Events.info(apiUser.getId(), uriInfo.getRequestUri(), Event.HttpMethod.GET, String.format("Read %d Imports", imports.size()));
            return imports;
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
    @ApiOperation(value = "Read Import", response = Import.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 401, message = "Unauthorized")})
    public Import readImport(
            @Auth User apiUser,
            @ApiParam(value = "Import ID", required = true) @PathParam("id") final String id,
            @ApiParam(value = "Day (YYYYMMDD)", required = false) @QueryParam("day") IntParam day) {
        try {
            Import licenseImport = Imports.readImport(id, day == null ? (new Tuid(id)).getYearMonthDay() : day.get());
            if (licenseImport == null) throw new WebApplicationException(Response.Status.NOT_FOUND);
            Events.info(apiUser.getId(), licenseImport.getId(), uriInfo.getRequestUri(), Event.HttpMethod.GET, String.format("Read Import %s", licenseImport.getId()));
            return licenseImport;
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
    @ApiOperation(value = "Delete Import")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "No Content"), @ApiResponse(code = 401, message = "Unauthorized")})
    public Response deleteImport(
            @Auth User apiUser,
            @ApiParam(value = "Import ID", required = true) @PathParam("id") final String id,
            @ApiParam(value = "Day (YYYYMMDD)", required = false) @QueryParam("day") IntParam day) {
        try {
            Import licenseImport = Imports.readImport(id, day == null ? (new Tuid(id)).getYearMonthDay() : day.get());
            if (licenseImport == null) throw new WebApplicationException(Response.Status.NOT_FOUND);
            Imports.deleteImport(id, day == null ? (new Tuid(id)).getYearMonthDay() : day.get());
            Events.info(apiUser.getId(), id, uriInfo.getRequestUri(), Event.HttpMethod.DELETE, String.format("Deleted Import %s", id));
            return Response.noContent().build();
        } catch (WebApplicationException e) {
            throw e; // rethrow web application exceptions and log the rest
        } catch (Exception e) {
            Events.error(apiUser.getId(), id, uriInfo.getRequestUri(), Event.HttpMethod.DELETE, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }
}
