package edu.itc.shop;

import static org.assertj.core.api.Assertions.assertThat;

import edu.itc.shop.web.LoginRequest;
import edu.itc.shop.web.dto.TokenResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestPropertySource(properties = "JWT_SECRET=0123456789abcdef0123456789abcdef0123456789abcdef")
class AuthFlowIT {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JwtEncoder encoder;

    @Test
    void loginThenAccessOrders() {
        var login = rest.postForEntity("/api/auth/login",
                new LoginRequest("alice", "test-only-alice-1A"), TokenResponse.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody().accessToken()).isNotBlank();
        assertThat(login.getHeaders().getFirst("Set-Cookie")).isNull();

        var headers = new HttpHeaders();
        headers.setBearerAuth(login.getBody().accessToken());
        var orders = rest.exchange("/api/orders", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        assertThat(orders.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void expiredTokenIs401() {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer("shop-api")
                .subject("alice")
                .issuedAt(now.minusSeconds(3600))
                .expiresAt(now.minusSeconds(1800))
                .claim("roles", List.of("ROLE_CUSTOMER"))
                .build();
        String expired = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        var headers = new HttpHeaders();
        headers.setBearerAuth(expired);
        var resp = rest.exchange("/api/orders", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getHeaders().getFirst("WWW-Authenticate")).contains("invalid_token");
        assertThat(resp.getBody()).contains("expired");
    }
}