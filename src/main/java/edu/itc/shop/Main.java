package edu.itc.shop;

import edu.itc.shop.domain.ProductService;
import java.net.URI;
import jakarta.inject.Singleton;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.inject.hk2.AbstractBinder;
import org.glassfish.jersey.server.ResourceConfig;
import edu.itc.shop.domain.InMemoryProductRepository;
import edu.itc.shop.domain.ProductRepository;
import edu.itc.shop.domain.ProductService;
import edu.itc.shop.domain.ReviewRepository;

public class Main {
    public static final URI BASE = URI.create("http://localhost:8080/api/");

    /** The application: scanned resources/providers + one shared repository and service. */
    public static ResourceConfig config() {
        return new ResourceConfig()
                .packages("edu.itc.shop.api")
                .register(new AbstractBinder() {
                    @Override
                    protected void configure() {
                        
                        ProductRepository repo = new InMemoryProductRepository();
                        bind(repo).to(ProductRepository.class);
                        bind(new ProductService(repo)).to(ProductService.class);
                        ReviewRepository reviewRepo = new ReviewRepository();
                        bind(reviewRepo).to(ReviewRepository.class);
                    }
                });
    }

    public static void main(String[] args) throws Exception {
        HttpServer server = GrizzlyHttpServerFactory.createHttpServer(BASE, config());
        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdownNow));
        System.out.println("shop-rest listening on " + BASE);
        Thread.currentThread().join();
        
    }
}