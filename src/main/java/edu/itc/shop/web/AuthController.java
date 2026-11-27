package edu.itc.shop.web;

import edu.itc.shop.config.SecurityProps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
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
import edu.itc.shop.web.dto.TokenResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authManager;
    private final JwtEncoder encoder;
    private final SecurityProps props;

    public AuthController(AuthenticationManager authManager, JwtEncoder encoder, SecurityProps props) {
        this.authManager = authManager;
        this.encoder = encoder;
        this.props = props;
    }

    @PostMapping("/login")
    public TokenResponse login(@RequestBody @Valid LoginRequest req) {
        Authentication auth = authManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(req.username(), req.password()));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("shop-api")
                .subject(auth.getName())
                .issuedAt(now)
                .expiresAt(now.plus(props.accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .claim("roles", auth.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority).toList())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", props.accessTokenTtl().toSeconds());
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail badLogin(HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Bad credentials");
        pd.setInstance(URI.create(req.getRequestURI()));
        return pd;
    }
}