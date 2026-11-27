package edu.itc.shop.web;

import edu.itc.shop.domain.Role;
import edu.itc.shop.domain.SecurityAudit;
import edu.itc.shop.service.AccountService;
import edu.itc.shop.service.AuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AuditService audit;
    private final AccountService accounts;

    public AdminController(AuditService audit, AccountService accounts) {
        this.audit = audit;
        this.accounts = accounts;
    }

    @GetMapping("/audit")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AuditEntry> audit(@RequestParam(required = false) String username) {
        return audit.recent(username).stream().map(AuditEntry::from).toList();
    }

    @PutMapping("/users/{username}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeRoles(@PathVariable String username, @RequestBody @Valid RoleUpdateRequest request,
                            Authentication actor) {
        accounts.changeRoles(username, request.roles(), actor.getName());
    }

    public record RoleUpdateRequest(@NotEmpty Set<Role> roles) {}

    public record AuditEntry(Long id, Instant at, String type, String username, String ip,
                             String requestId, String detail) {
        static AuditEntry from(SecurityAudit audit) {
            return new AuditEntry(audit.getId(), audit.getAt(), audit.getType(), audit.getUsername(),
                    audit.getIp(), audit.getRequestId(), audit.getDetail());
        }
    }
}
