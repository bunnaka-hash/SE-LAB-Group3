package edu.itc.shop.config;

import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Task 5: CORS for exactly one front-end origin. Any other Origin gets no
 * Access-Control-Allow-Origin header, so the browser blocks the response.
 * Picked up by http.cors(Customizer.withDefaults()) in SecurityConfig.
 */
@Configuration
public class CorsConfig {

    static final String FRONTEND_ORIGIN = "http://localhost:5173";

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of(FRONTEND_ORIGIN));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cfg.setExposedHeaders(List.of("Location"));
        cfg.setAllowCredentials(false);          // Bearer header, never cookies
        cfg.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cfg);
        return source;
    }
}
