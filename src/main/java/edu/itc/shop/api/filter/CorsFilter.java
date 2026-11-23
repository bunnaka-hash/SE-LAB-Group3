package edu.itc.shop.api.filter;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

@Provider
@PreMatching
@Priority(Priorities.AUTHENTICATION - 100)   // runs before ApiKeyFilter
public class CorsFilter implements ContainerRequestFilter, ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext req) {
        if ("OPTIONS".equals(req.getMethod())) {
            req.abortWith(Response.ok().build());
        }
    }

    @Override
    public void filter(ContainerRequestContext req, ContainerResponseContext res) {
        var h = res.getHeaders();
        h.putSingle("Access-Control-Allow-Origin", "http://localhost:5173");
        h.putSingle("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE");
        h.putSingle("Access-Control-Allow-Headers", "Content-Type,X-Api-Key,X-Correlation-Id,If-Match,Idempotency-Key");
        h.putSingle("Access-Control-Expose-Headers", "Location,ETag,Link,X-Total-Count,X-Correlation-Id,Idempotency-Replayed");
    }
}