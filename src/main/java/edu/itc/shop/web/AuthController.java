package edu.itc.shop.web;

import edu.itc.shop.config.SecurityProps;
import edu.itc.shop.domain.UserAccount;
import edu.itc.shop.repository.UserAccountRepository;
import edu.itc.shop.service.InvalidRefreshTokenException;
import edu.itc.shop.service.LoginRateLimitException;
import edu.itc.shop.service.RateLimitService;
import edu.itc.shop.service.RefreshTokenService;
import edu.itc.shop.web.dto.TokenResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authManager;
    private final JwtEncoder encoder;
    private final SecurityProps props;
    private final RefreshTokenService refreshTokenService;
    private final RateLimitService rateLimitService;
    private final UserAccountRepository users;

    public AuthController(AuthenticationManager authManager, JwtEncoder encoder, SecurityProps props,
                          RefreshTokenService refreshTokenService, RateLimitService rateLimitService,
                          UserAccountRepository users) {
        this.authManager = authManager;
        this.encoder = encoder;
        this.props = props;
        this.refreshTokenService = refreshTokenService;
        this.rateLimitService = rateLimitService;
        this.users = users;
    }

    @PostMapping("/login")
    public TokenResponse login(@RequestBody @Valid LoginRequest req, HttpServletRequest request) {
        long retryAfter = rateLimitService.recordAttempt(request.getRemoteAddr(), req.username());
        if (retryAfter > 0) {
            throw new LoginRateLimitException(retryAfter);
        }

        Authentication auth = authManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(req.username(), req.password()));
        String refreshToken = refreshTokenService.createTokenForUser(
                auth.getName(), UUID.randomUUID().toString());
        return response(auth.getName(),
                auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList(),
                refreshToken);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@RequestBody @Valid RefreshRequest req) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(req.refreshToken());
        if (!rotation.isValid()) {
            throw new InvalidRefreshTokenException();
        }

        UserAccount account = users.findByUsername(rotation.username())
                .orElseThrow(InvalidRefreshTokenException::new);
        return response(account.getUsername(),
                account.getRoles().stream().map(role -> "ROLE_" + role.name()).toList(),
                rotation.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody @Valid LogoutRequest req) {
        refreshTokenService.logout(req.refreshToken());
    }

    private TokenResponse response(String username, Collection<String> roles, String refreshToken) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("shop-api")
                .subject(username)
                .issuedAt(now)
                .expiresAt(now.plus(props.accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .claim("roles", roles)
                .build();
        String accessToken = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new TokenResponse(accessToken, refreshToken, "Bearer", props.accessTokenTtl().toSeconds());
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail badLogin(HttpServletRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Bad credentials");
        detail.setInstance(URI.create(request.getRequestURI()));
        return detail;
    }
}
