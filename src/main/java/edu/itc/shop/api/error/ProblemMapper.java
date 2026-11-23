package edu.itc.shop.api.error;

import java.util.UUID;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import edu.itc.shop.api.dto.Problem;

@Provider
public class ProblemMapper implements ExceptionMapper<Throwable> {
    private static final System.Logger LOG = System.getLogger("api");

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(Throwable ex) {
        String path = uriInfo.getRequestUri().getPath();

        if (ex instanceof WebApplicationException wae) {
            Response original = wae.getResponse();
            int status = original.getStatus();
            if (status < 400)
                return original; // redirects / 304 pass through untouched
            String title = original.getStatusInfo().getReasonPhrase();
            var problem = new Problem("about:blank", title, status, wae.getMessage(), path, null);
            // fromResponse keeps headers such as Allow or WWW-Authenticate
            return Response.fromResponse(original).type(Problem.MEDIA_TYPE).entity(problem).build();
        }

        // Anything unexpected: log the details, tell the client only a reference id
        String ref = UUID.randomUUID().toString();
        LOG.log(System.Logger.Level.ERROR, "Unhandled error, reference " + ref + " on " + path, ex);
        var problem = new Problem("about:blank", "Internal Server Error", 500,
                "reference " + ref, path, null);
        return Response.status(500).type(Problem.MEDIA_TYPE).entity(problem).build();
    }
}