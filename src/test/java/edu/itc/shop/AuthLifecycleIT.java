package edu.itc.shop;

import static org.assertj.core.api.Assertions.assertThat;

import edu.itc.shop.domain.RefreshToken;
import edu.itc.shop.repository.RefreshTokenRepository;
import edu.itc.shop.repository.SecurityAuditRepository;
import edu.itc.shop.repository.UserAccountRepository;
import edu.itc.shop.service.RefreshTokenService;
import edu.itc.shop.web.LoginRequest;
import edu.itc.shop.web.LogoutRequest;
import edu.itc.shop.web.RefreshRequest;
import edu.itc.shop.web.dto.TokenResponse;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = {
        "shop.security.jwt-secret=0123456789abcdef0123456789abcdef0123456789abcdef",
        "shop.seed.admin-password=test-only-admin-1A",
        "shop.seed.customer-password=test-only-alice-1A",
        "spring.datasource.url=jdbc:h2:mem:auth-lifecycle"})
class AuthLifecycleIT {

    private static final String CUSTOMER = "alice";
    private static final String CUSTOMER_PASSWORD = "test-only-alice-1A";

    @Autowired
    TestRestTemplate rest;

    @Autowired
    RefreshTokenRepository refreshTokens;

    @Autowired
    RefreshTokenService refreshTokenService;

    @Autowired
    UserAccountRepository users;

    @Autowired
    SecurityAuditRepository audits;

    @Test
    void rotatesRefreshTokensAndRevokesFamilyOnReuse() {
        TokenResponse login = login(CUSTOMER, CUSTOMER_PASSWORD).getBody();
        assertThat(login).isNotNull();
        assertThat(login.refreshToken()).matches("[A-Za-z0-9_-]{43}");
        assertThat(Base64.getUrlDecoder().decode(login.refreshToken())).hasSize(32);
        assertThat(login.accessToken()).isNotBlank();
        assertThat(rest.getForEntity("/api/orders", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(accessOrders(login.accessToken())).isEqualTo(HttpStatus.OK.value());

        ResponseEntity<TokenResponse> rotatedResponse = rest.postForEntity("/api/auth/refresh",
                new RefreshRequest(login.refreshToken()), TokenResponse.class);
        assertThat(rotatedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        TokenResponse rotated = rotatedResponse.getBody();
        assertThat(rotated).isNotNull();
        assertThat(rotated.refreshToken()).isNotEqualTo(login.refreshToken());

        assertThat(refreshInvalid(login.refreshToken()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(accessOrders(rotated.accessToken())).isEqualTo(HttpStatus.OK.value());
        assertThat(refreshInvalid(login.refreshToken()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(refreshInvalid(rotated.refreshToken()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        RefreshToken initialRow = refreshTokens.findByTokenHash(refreshTokenService.hash(login.refreshToken()))
                .orElseThrow();
        List<RefreshToken> family = refreshTokens.findByFamilyId(initialRow.getFamilyId());
        assertThat(family).hasSize(2).allSatisfy(token -> assertThat(token.getRevokedAt()).isNotNull());
        assertThat(initialRow.getReplacedBy()).isNotNull();
        assertThat(initialRow.getTokenHash()).isNotEqualTo(login.refreshToken()).hasSize(64);
        assertThat(audits.findTop100ByTypeOrderByAtDesc("TOKEN_REFRESHED")).isNotEmpty();
        assertThat(audits.findTop100ByTypeOrderByAtDesc("REFRESH_REUSE_DETECTED")).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void fiveConsecutiveFailuresLockTheAccountAndAreAudited() {
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(loginFailure(CUSTOMER, "wrong-password").getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        var account = users.findByUsername(CUSTOMER).orElseThrow();
        assertThat(account.getFailedAttempts()).isEqualTo(5);
        assertThat(account.getLockedUntil()).isAfter(Instant.now());
        assertThat(audits.findTop100ByTypeOrderByAtDesc("LOGIN_FAILED")).hasSizeGreaterThanOrEqualTo(5);
        assertThat(audits.findTop100ByTypeOrderByAtDesc("LOCKED")).isNotEmpty();
    }

    @Test
    void loginRateLimitReturnsProblemDetailsAndRetryAfter() {
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(loginFailure(CUSTOMER, "wrong-password").getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        ResponseEntity<String> limited = loginFailure(CUSTOMER, "wrong-password");
        assertThat(limited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(limited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).matches("\\d+");
        assertThat(limited.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void adminAuditEndpointIsRestrictedAndRecordsRoleChangesAndDenials() {
        TokenResponse admin = login("admin", "test-only-admin-1A").getBody();
        TokenResponse customer = login(CUSTOMER, CUSTOMER_PASSWORD).getBody();
        assertThat(admin).isNotNull();
        assertThat(customer).isNotNull();

        assertThat(getAudit(admin.accessToken()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getAudit(customer.accessToken()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(admin.accessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Void> changed = rest.exchange("/api/admin/users/alice/roles", HttpMethod.PUT,
                new HttpEntity<>("{\"roles\":[\"CUSTOMER\"]}", headers), Void.class);
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(audits.findTop100ByTypeOrderByAtDesc("ROLE_CHANGED")).isNotEmpty();
        assertThat(audits.findTop100ByTypeOrderByAtDesc("ACCESS_DENIED"))
                .anySatisfy(event -> assertThat(event.getRequestId()).isNotBlank());
    }

    @Test
    void logoutRevokesTheRefreshTokenFamily() {
        TokenResponse login = login(CUSTOMER, CUSTOMER_PASSWORD).getBody();
        assertThat(login).isNotNull();

        ResponseEntity<Void> logout = rest.postForEntity("/api/auth/logout",
                new LogoutRequest(login.refreshToken()), Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(refreshInvalid(login.refreshToken()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        RefreshToken stored = refreshTokens.findByTokenHash(refreshTokenService.hash(login.refreshToken()))
                .orElseThrow();
        assertThat(stored.getRevokedAt()).isNotNull();
        assertThat(audits.findTop100ByTypeOrderByAtDesc("LOGOUT")).isNotEmpty();
    }

    @Test
    void successfulLoginResetsConsecutiveFailures() {
        assertThat(loginFailure(CUSTOMER, "wrong-password").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(loginFailure(CUSTOMER, "wrong-password").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(users.findByUsername(CUSTOMER).orElseThrow().getFailedAttempts()).isEqualTo(2);

        assertThat(login(CUSTOMER, CUSTOMER_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
        var account = users.findByUsername(CUSTOMER).orElseThrow();
        assertThat(account.getFailedAttempts()).isZero();
        assertThat(account.getLockedUntil()).isNull();
    }

    private ResponseEntity<TokenResponse> login(String username, String password) {
        return login(username, password, TokenResponse.class);
    }

    private <T> ResponseEntity<T> login(String username, String password, Class<T> responseType) {
        return rest.postForEntity("/api/auth/login", new LoginRequest(username, password), responseType);
    }

    private ResponseEntity<String> loginFailure(String username, String password) {
        return login(username, password, String.class);
    }

    private ResponseEntity<String> refreshInvalid(String refreshToken) {
        return rest.postForEntity("/api/auth/refresh", new RefreshRequest(refreshToken), String.class);
    }

    private int accessOrders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return rest.exchange("/api/orders", HttpMethod.GET, new HttpEntity<>(headers), String.class)
                .getStatusCode().value();
    }

    private ResponseEntity<String> getAudit(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return rest.exchange("/api/admin/audit", HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
}
