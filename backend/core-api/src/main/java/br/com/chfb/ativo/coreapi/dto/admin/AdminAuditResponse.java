package br.com.chfb.ativo.coreapi.dto.admin;

import br.com.chfb.ativo.coreapi.entity.AdminAuditEntry;
import java.time.Instant;
import java.util.UUID;

public record AdminAuditResponse(UUID id, String actorEmail, String action, String target, String details, Instant createdAt) {

    public static AdminAuditResponse from(AdminAuditEntry entry) {
        return new AdminAuditResponse(
                entry.getId(),
                entry.getActorEmail(),
                entry.getAction(),
                entry.getTarget(),
                entry.getDetails(),
                entry.getCreatedAt());
    }
}
