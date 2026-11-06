package br.com.chfb.ativo.coreapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Só existe para o que o admin alterou; sem registro, vale o {@code application.yml}. */
@Entity
@Table(name = "app_settings")
public class AppSetting {

    @Id
    @Column(name = "key")
    private String key;

    @Column(nullable = false)
    private String value;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    protected AppSetting() {
    }

    public AppSetting(String key) {
        this.key = key;
    }

    public void change(String value, String updatedBy, Instant updatedAt) {
        this.value = value;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }
}
