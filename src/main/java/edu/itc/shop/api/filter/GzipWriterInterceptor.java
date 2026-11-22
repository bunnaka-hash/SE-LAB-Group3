package edu.itc.shop.api.filter;

import java.io.IOException;
import java.util.zip.GZIPOutputStream;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.WriterInterceptor;
import jakarta.ws.rs.ext.WriterInterceptorContext;

@Provider
@Compress
public class GzipWriterInterceptor implements WriterInterceptor {
    @Context HttpHeaders requestHeaders;      // providers may use @Context injection

    @Override
    public void aroundWriteTo(WriterInterceptorContext ctx) throws IOException {
        String enc = requestHeaders.getHeaderString(HttpHeaders.ACCEPT_ENCODING);
        if (enc != null && enc.contains("gzip")) {
            var original = ctx.getOutputStream();
            var gz = new GZIPOutputStream(original);
            ctx.getHeaders().putSingle("Content-Encoding", "gzip");
            ctx.getHeaders().putSingle("Vary", "Accept-Encoding");
            ctx.getHeaders().remove("Content-Length");   // length changes after compression
            ctx.setOutputStream(gz);
            try {
                ctx.proceed();
            } finally {
                gz.finish();                             // flush the gzip trailer
                ctx.setOutputStream(original);
            }
            return;
        }
        ctx.proceed();
    }
}