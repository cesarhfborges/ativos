package br.com.chfb.ativo.coreapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "admin_audit_log")
public class AdminAuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "actor_email", nullable = false, updatable = false)
    private String actorEmail;

    @Column(nullable = false, updatable = false)
    private String action;

    @Column(updatable = false)
    private String target;

    @Column(updatable = false)
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AdminAuditEntry() {
    }

    public AdminAuditEntry(String actorEmail, String action, String target, String details, Instant createdAt) {
        this.actorEmail = actorEmail;
        this.action = action;
        this.target = target;
        this.details = details;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getActorEmail() {
        return actorEmail;
    }

    public String getAction() {
        return action;
    }

    public String getTarget() {
        return target;
    }

    public String getDetails() {
        return details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
