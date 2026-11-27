package edu.itc.shop.service;

import edu.itc.shop.domain.UserAccount;
import edu.itc.shop.repository.UserAccountRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LockoutService {

    private static final int MAX_FAILURES = 5;
    private static final long LOCK_MINUTES = 15;

    public enum FailureStatus {
        UNKNOWN_USER,
        FAILED,
        LOCKED,
        LOCKED_NOW
    }

    private final UserAccountRepository users;

    public LockoutService(UserAccountRepository users) {
        this.users = users;
    }

    @Transactional
    public FailureStatus recordFailure(String username) {
        UserAccount account = users.findByUsernameForUpdate(username).orElse(null);
        if (account == null) {
            return FailureStatus.UNKNOWN_USER;
        }

        Instant now = Instant.now();
        if (account.isLockedAt(now)) {
            return FailureStatus.LOCKED;
        }

        if (account.getLockedUntil() != null) {
            account.setLockedUntil(null);
            account.setFailedAttempts(0);
        }

        int failures = account.getFailedAttempts() + 1;
        account.setFailedAttempts(failures);
        if (failures >= MAX_FAILURES) {
            account.setLockedUntil(now.plus(LOCK_MINUTES, ChronoUnit.MINUTES));
            users.save(account);
            return FailureStatus.LOCKED_NOW;
        }

        users.save(account);
        return FailureStatus.FAILED;
    }

    @Transactional
    public void recordSuccess(String username) {
        users.findByUsernameForUpdate(username).ifPresent(account -> {
            account.setFailedAttempts(0);
            account.setLockedUntil(null);
            users.save(account);
        });
    }
}
