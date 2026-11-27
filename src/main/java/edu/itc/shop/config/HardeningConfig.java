package edu.itc.shop.config;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authorization.AuthorizationEventPublisher;
import org.springframework.security.authorization.SpringAuthorizationEventPublisher;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import edu.itc.shop.security.RequestIdFilter;

/**
 * Task 5 security configuration. Everything lives in this new class so the Task 1-4
 * classes (SecurityConfig, JwtConfig) stay exactly as they were:
 * <ul>
 *   <li>@Order(1) chain for /api/**: Bearer JWT, CORS, hardened headers, request id</li>
 *   <li>@Order(2) chain for the Thymeleaf review page /products/**</li>
 *   <li>the older chains in SecurityConfig have no @Order, so they are evaluated after these two</li>
 *   <li>authorization / authentication event publishers for SecurityEventLogger</li>
 *   <li>registration of the @Validated SecurityProps record (fail fast without JWT_SECRET)</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(SecurityProps.class)
public class HardeningConfig {

    @Bean
    @Order(1)
    SecurityFilterChain hardenedApi(HttpSecurity http, JwtAuthenticationConverter conv,
                                    ProblemAuthEntryPoint entryPoint,
                                    @Value("${shop.security.hsts-over-http:false}") boolean hstsOverHttp) throws Exception {
        http.securityMatcher("/api/**")
            .cors(Customizer.withDefaults())
            // CSRF disabled: this chain accepts Bearer tokens only, never cookies (see docs/csrf.md)
            .csrf(csrf -> csrf.disable())
            .headers(h -> hardenHeaders(h, hstsOverHttp))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/register").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()    // includes /{id}/reviews
                .requestMatchers(HttpMethod.HEAD, "/api/products/**").permitAll()   // curl -I
                .requestMatchers(HttpMethod.DELETE, "/api/products/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .oauth2ResourceServer(o -> o.authenticationEntryPoint(entryPoint)
                                        .jwt(j -> j.jwtAuthenticationConverter(conv)))
            .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint))
            // created here only (not a @Component) so it runs once; before the bearer filter and
            // therefore before AuthorizationFilter, so failed token checks carry a rid as well
            .addFilterBefore(new RequestIdFilter(), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    /** Thymeleaf review page (GET only, public). */
    @Bean
    @Order(2)
    SecurityFilterChain pages(HttpSecurity http,
                              @Value("${shop.security.hsts-over-http:false}") boolean hstsOverHttp) throws Exception {
        http.securityMatcher("/products/**")
            .headers(h -> hardenHeaders(h, hstsOverHttp))
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.GET, "/products/**").permitAll()
                .anyRequest().denyAll());
        return http.build();
    }

    /** Publishes AuthorizationDeniedEvent so SecurityEventLogger can log denials. */
    @Bean
    AuthorizationEventPublisher authorizationEventPublisher(ApplicationEventPublisher publisher) {
        return new SpringAuthorizationEventPublisher(publisher);
    }

    /**
     * The login endpoint uses its own ProviderManager (JwtConfig), which does not publish
     * events by itself. Done here instead of editing JwtConfig.
     */
    @Bean
    SmartInitializingSingleton authenticationEventWiring(AuthenticationManager manager,
                                                         AuthenticationEventPublisher publisher) {
        return () -> {
            if (manager instanceof ProviderManager pm) {
                pm.setAuthenticationEventPublisher(publisher);
            }
        };
    }

    static void hardenHeaders(HeadersConfigurer<HttpSecurity> h, boolean hstsOverHttp) {
        h.frameOptions(f -> f.deny())
         .httpStrictTransportSecurity(hsts -> {
             hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000);
             // Lab only: HSTS is normally written for HTTPS requests only. Production keeps the default.
             if (hstsOverHttp) {
                 hsts.requestMatcher(AnyRequestMatcher.INSTANCE);
             }
         })
         .contentSecurityPolicy(csp -> csp.policyDirectives(
             "default-src 'self'; frame-ancestors 'none'; object-src 'none'"))
         .referrerPolicy(r -> r.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
    }
}
