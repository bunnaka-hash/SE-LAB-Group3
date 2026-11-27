package edu.itc.shop.service;

import edu.itc.shop.domain.Role;
import edu.itc.shop.domain.UserAccount;
import edu.itc.shop.domain.Role;
import edu.itc.shop.repository.UserAccountRepository;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.Set;

@Service
public class AccountService {

    private final UserAccountRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public AccountService(UserAccountRepository users, PasswordEncoder encoder, AuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
    }

    @Transactional
    public UserAccount register(String username, String rawPassword, Set<Role> roles) {
        if (users.existsByUsername(username)) {
            throw new UsernameTakenException(username);
        }
        UserAccount account = new UserAccount(username, encoder.encode(rawPassword), roles);
        return users.save(account);
    }

    @Transactional
    public void changeRoles(String username, Set<Role> roles, String actor) {
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("At least one role is required");
        }
        UserAccount account = users.findByUsernameForUpdate(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        String oldRoles = roleNames(account.getRoles());
        String newRoles = roleNames(roles);
        account.setRoles(roles);
        users.save(account);
        audit.log("ROLE_CHANGED", username,
                "roles changed by " + actor + " from [" + oldRoles + "] to [" + newRoles + "]");
    }

    private String roleNames(Set<Role> roles) {
        return roles.stream().map(Role::name).sorted().collect(Collectors.joining(","));
    }
}