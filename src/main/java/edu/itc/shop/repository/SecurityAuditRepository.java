package edu.itc.shop.repository;

import edu.itc.shop.domain.SecurityAudit;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityAuditRepository 
extends JpaRepository<SecurityAudit, Long> {
    List<SecurityAudit> findByUsername(String username);

    List<SecurityAudit> findTop100ByOrderByAtDesc();

    List<SecurityAudit> findTop100ByUsernameOrderByAtDesc(String username);

    List<SecurityAudit> findTop100ByTypeOrderByAtDesc(String type);
}
