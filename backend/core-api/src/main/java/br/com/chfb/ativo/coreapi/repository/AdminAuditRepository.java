package br.com.chfb.ativo.coreapi.repository;

import br.com.chfb.ativo.coreapi.entity.AdminAuditEntry;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditRepository extends JpaRepository<AdminAuditEntry, UUID> {
}
