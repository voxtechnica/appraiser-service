package info.voxtechnica.appraisers.resource;

import com.codahale.metrics.annotation.Timed;
import com.wordnik.swagger.annotations.*;
import info.voxtechnica.appraisers.db.dao.Events;
import info.voxtechnica.appraisers.db.dao.Licenses;
import info.voxtechnica.appraisers.model.Event;
import info.voxtechnica.appraisers.model.License;
import info.voxtechnica.appraisers.model.User;
import io.dropwizard.auth.Auth;
import io.dropwizard.jersey.caching.CacheControl;
import io.dropwizard.jersey.params.IntParam;
import org.apache.commons.lang3.exception.ExceptionUtils;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.List;
import java.util.SortedSet;

@Path("/v1/licenses")
@Api(value = "/v1/licenses", description = "Licenses")
public class LicenseResource {
    @Context
    private UriInfo uriInfo;

    @PermitAll
    @GET
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    @CacheControl(noCache = true, noStore = true, mustRevalidate = true, maxAge = 0)
    @ApiOperation(value = "List Licenses", response = License.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 401, message = "Unauthorized")})
    public SortedSet<License> readLicenses(
            @Auth User apiUser,
            @ApiParam(value = "State", required = false) @QueryParam("state") String state,
            @ApiParam(value = "License Number", required = false) @QueryParam("license_number") String licenseNumber,
            @ApiParam(value = "Update Day (YYYYMMDD)", required = false) @QueryParam("day") IntParam day,
            @ApiParam(value = "Number of Licenses", required = false) @QueryParam("limit") @DefaultValue("100") IntParam limit,
            @ApiParam(value = "Last License ID", required = false) @QueryParam("offset") String offset) {
        try {
            SortedSet<License> licenses;
            if (licenseNumber != null) {
                licenses = Licenses.readLicensesByLicenseNumber(licenseNumber);
                // filter by state if provided
                if (state != null) for (License license : licenses)
                    if (!state.equalsIgnoreCase(license.getState())) licenses.remove(license);
            } else if (state != null) licenses = Licenses.readLicensesByState(state, limit.get(), offset);
            else if (day != null) licenses = Licenses.readLicenseUpdatesByDay(day.get(), limit.get());
            else licenses = Licenses.readLicenses(limit.get());
            Events.info(apiUser.getId(), uriInfo.getRequestUri(), Event.HttpMethod.GET, String.format("Read %d Licenses", licenses.size()));
            return licenses;
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
    @ApiOperation(value = "Read License", response = License.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 401, message = "Unauthorized")})
    public License readLicense(@Auth User apiUser, @ApiParam(value = "License ID", required = true) @PathParam("id") final String id) {
        try {
            License license = Licenses.readLicense(id);
            if (license == null) throw new WebApplicationException(Response.Status.NOT_FOUND);
            Events.info(apiUser.getId(), license.getId(), uriInfo.getRequestUri(), Event.HttpMethod.GET, String.format("Read License %s", license.getId()));
            return license;
        } catch (WebApplicationException e) {
            throw e; // rethrow web application exceptions and log the rest
        } catch (Exception e) {
            Events.error(apiUser.getId(), id, uriInfo.getRequestUri(), Event.HttpMethod.GET, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @PermitAll
    @Path("/{id}/versions")
    @GET
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    @CacheControl(noCache = true, noStore = true, mustRevalidate = true, maxAge = 0)
    @ApiOperation(value = "Get License revision history", response = License.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 401, message = "Unauthorized")})
    public List<License> readLicenseVersions(
            @Auth User apiUser,
            @ApiParam(value = "License ID", required = true) @PathParam("id") final String id) {
        try {
            List<License> versions = Licenses.readLicenseVersions(id);
            Events.info(apiUser.getId(), id, uriInfo.getRequestUri(), Event.HttpMethod.GET,
                    String.format("Listed License %s (%d versions)", id, versions.size()));
            return versions;
        } catch (WebApplicationException e) {
            throw e; // rethrow web application exceptions and log the rest
        } catch (Exception e) {
            Events.error(apiUser.getId(), id, uriInfo.getRequestUri(), Event.HttpMethod.GET, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @PermitAll
    @Path("/{id}/versions/{update_id}")
    @GET
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    @CacheControl(immutable = true)
    @ApiOperation(value = "Get License version", response = License.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 404, message = "Not Found")})
    public License readLicenseVersion(
            @Auth User apiUser,
            @ApiParam(value = "License ID", required = true) @PathParam("id") final String id,
            @ApiParam(value = "Update ID", required = true) @PathParam("update_id") final String updateId) {
        try {
            License license = Licenses.readLicenseVersion(id, updateId);
            if (license == null) throw new WebApplicationException(Response.Status.NOT_FOUND);
            Events.info(apiUser.getId(), id, uriInfo.getRequestUri(), Event.HttpMethod.GET,
                    String.format("Read License %s Update %s", id, updateId));
            return license;
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
    @ApiOperation(value = "Delete License")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "No Content"), @ApiResponse(code = 401, message = "Unauthorized")})
    public Response deleteLicense(@Auth User apiUser, @ApiParam(value = "License ID", required = true) @PathParam("id") final String id) {
        try {
            License license = Licenses.readLicense(id);
            if (license == null) throw new WebApplicationException(Response.Status.NOT_FOUND);
            Licenses.deleteLicense(id);
            Events.info(apiUser.getId(), id, uriInfo.getRequestUri(), Event.HttpMethod.DELETE, String.format("Deleted License %s", id));
            return Response.noContent().build();
        } catch (WebApplicationException e) {
            throw e; // rethrow web application exceptions and log the rest
        } catch (Exception e) {
            Events.error(apiUser.getId(), id, uriInfo.getRequestUri(), Event.HttpMethod.DELETE, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }
}
