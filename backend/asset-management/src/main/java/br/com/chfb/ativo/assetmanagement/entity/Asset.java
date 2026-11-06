package br.com.chfb.ativo.assetmanagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Criado apenas por {@code AssetRepository.insertIfAbsent}, que resolve cadastros simultâneos. */
@Entity
@Table(name = "assets")
public class Asset {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String symbol;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false)
    private AssetType assetType;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false)
    private VerificationStatus verificationStatus;

    protected Asset() {
    }

    /** Muda para todos os usuários: o catálogo é compartilhado. */
    public void update(String name, AssetType assetType) {
        this.name = name;
        this.assetType = assetType;
    }

    public void markVerification(VerificationStatus verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public UUID getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public AssetType getAssetType() {
        return assetType;
    }
}
