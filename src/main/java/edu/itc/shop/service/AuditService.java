
package edu.itc.shop.service;

import edu.itc.shop.domain.SecurityAudit;
import edu.itc.shop.repository.SecurityAuditRepository;
import edu.itc.shop.security.RequestIdFilter;
import java.time.Instant;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditService {

    private final SecurityAuditRepository repository;

    public AuditService(
            SecurityAuditRepository repository) {
        this.repository = repository;
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW)
    public void log(
            String type,
            String username,
            String detail) {

        SecurityAudit audit =
                new SecurityAudit();

        var attributes = RequestContextHolder.getRequestAttributes();
        audit.setAt(Instant.now());
        audit.setType(type);
        audit.setUsername(username);
        audit.setIp(attributes instanceof ServletRequestAttributes servletAttributes
                ? servletAttributes.getRequest().getRemoteAddr()
                : null);
        audit.setRequestId(MDC.get(RequestIdFilter.MDC_KEY));
        audit.setDetail(detail);

        repository.save(audit);
    }

    @Transactional(readOnly = true)
    public List<SecurityAudit> recent(String username) {
        return username == null || username.isBlank()
                ? repository.findTop100ByOrderByAtDesc()
                : repository.findTop100ByUsernameOrderByAtDesc(username);
    }
}