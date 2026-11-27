package edu.itc.shop.service;

import edu.itc.shop.domain.RefreshToken;
import edu.itc.shop.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long TOKEN_LIFETIME_DAYS = 7;

    private final RefreshTokenRepository repository;
    private final AuditService audit;

    public RefreshTokenService(
            RefreshTokenRepository repository,
            AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    public String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    public String hash(String token) {
        try {
            byte[] result = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(result);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    @Transactional
    public String createTokenForUser(
            String username,
            String familyId) {
        String refreshToken = generateToken();
        repository.save(newToken(username, familyId, refreshToken));
        return refreshToken;
    }

    @Transactional
    public Rotation rotate(String presentedToken) {
        RefreshToken current = repository.findByTokenHashForUpdate(hash(presentedToken))
                .orElse(null);
        if (current == null) {
            return Rotation.invalid();
        }

        if (current.getRevokedAt() != null) {
            if (current.getReplacedBy() != null) {
                revokeFamily(current.getFamilyId(), Instant.now());
                audit.log("REFRESH_REUSE_DETECTED", current.getUsername(),
                        "rotated refresh token reused; family revoked");
            }
            return Rotation.invalid();
        }

        Instant now = Instant.now();
        if (!current.getExpiresAt().isAfter(now)) {
            return Rotation.invalid();
        }

        String replacement = generateToken();
        RefreshToken next = repository.saveAndFlush(
                newToken(current.getUsername(), current.getFamilyId(), replacement));
        current.setRevokedAt(now);
        current.setReplacedBy(next.getId());
        repository.save(current);

        audit.log("TOKEN_REFRESHED", current.getUsername(), "refresh token rotated");
        return new Rotation(current.getUsername(), replacement);
    }

    @Transactional
    public void logout(String presentedToken) {
        RefreshToken token = repository.findByTokenHashForUpdate(hash(presentedToken))
                .orElse(null);
        if (token == null) {
            audit.log("LOGOUT", null, "logout requested for unknown refresh token");
            return;
        }
        revokeFamily(token.getFamilyId(), Instant.now());
        audit.log("LOGOUT", token.getUsername(), "refresh token family revoked");
    }

    private void revokeFamily(String familyId, Instant now) {
        List<RefreshToken> family = repository.findByFamilyIdForUpdate(familyId);
        for (RefreshToken token : family) {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(now);
            }
        }
        repository.saveAll(family);
    }

    private RefreshToken newToken(String username, String familyId, String rawToken) {
        RefreshToken token = new RefreshToken();
        token.setUsername(username);
        token.setFamilyId(familyId);
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(Instant.now().plus(TOKEN_LIFETIME_DAYS, ChronoUnit.DAYS));
        return token;
    }

    public record Rotation(String username, String refreshToken) {
        public static Rotation invalid() {
            return new Rotation(null, null);
        }

        public boolean isValid() {
            return refreshToken != null;
        }
    }
}