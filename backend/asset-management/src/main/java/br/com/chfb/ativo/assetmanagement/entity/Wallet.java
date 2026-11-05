package br.com.chfb.ativo.assetmanagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    protected Wallet() {
    }

    public Wallet(UUID userId, String name) {
        this.userId = userId;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }
}
