package edu.itc.shop.api.filter;

import java.io.IOException;
import java.util.UUID;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.*;
import jakarta.ws.rs.ext.Provider;

@Provider
@Priority(Priorities.USER)
public class CorrelationFilter implements ContainerRequestFilter, ContainerResponseFilter {
    private static final System.Logger LOG = System.getLogger(CorrelationFilter.class.getName());
    private static final String ID = "correlationId", START = "startNanos";

    @Override
    public void filter(ContainerRequestContext req) {
        System.out.println(getClass().getSimpleName()); // temporary, remove after the order check
        String id = req.getHeaderString("X-Correlation-Id");
        if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
        req.setProperty(ID, id);
        req.setProperty(START, System.nanoTime());
    }

    @Override
    public void filter(ContainerRequestContext req, ContainerResponseContext res) throws IOException {
        // If ApiKeyFilter aborted first, the request side never ran
        String id = (String) req.getProperty(ID);
        if (id == null) {
            id = req.getHeaderString("X-Correlation-Id");
            if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
        }
        Long start = (Long) req.getProperty(START);
        long ms = start == null ? 0 : (System.nanoTime() - start) / 1_000_000;
        res.getHeaders().putSingle("X-Correlation-Id", id);
        LOG.log(System.Logger.Level.INFO, "{0} {1} -> {2} [{3}] {4} ms",
                req.getMethod(), req.getUriInfo().getPath(true), res.getStatus(), id, ms);
    }
}