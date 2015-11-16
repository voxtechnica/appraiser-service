package info.voxtechnica.appraisers.resource;

import info.voxtechnica.appraisers.view.SwaggerView;
import io.dropwizard.jersey.caching.CacheControl;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.concurrent.TimeUnit;

@Path("/docs")
@Produces(MediaType.TEXT_HTML)
public class SwaggerResource {
    private final String applicationContextPath;

    public SwaggerResource(String applicationContextPath) {
        this.applicationContextPath = applicationContextPath;
    }

    @GET
    @CacheControl(maxAge = 1, maxAgeUnit = TimeUnit.HOURS)
    public SwaggerView get() {
        return new SwaggerView(applicationContextPath);
    }
}
