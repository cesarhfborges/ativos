package br.com.chfb.ativo.assetmanagement.repository;

import br.com.chfb.ativo.assetmanagement.entity.TransactionType;
import br.com.chfb.ativo.assetmanagement.entity.WalletTransaction;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {

    @Query("""
            select t from WalletTransaction t
            left join fetch t.asset
            where t.walletId = :walletId and (:type is null or t.type = :type)
            order by t.timestamp desc, t.recordedAt desc, t.id desc
            """)
    List<WalletTransaction> findStatement(
            @Param("walletId") UUID walletId, @Param("type") TransactionType type, Pageable pageable);

    Optional<WalletTransaction> findByIdAndWalletId(UUID id, UUID walletId);

    /** Operações válidas de um ativo, na ordem em que aconteceram; no mesmo instante, na ordem em que foram lançadas. */
    @Query("""
            select t from WalletTransaction t
            where t.walletId = :walletId and t.asset.id = :assetId and t.reversedAt is null
            order by t.timestamp, t.recordedAt, t.id
            """)
    List<WalletTransaction> findActiveByWalletAndAsset(@Param("walletId") UUID walletId, @Param("assetId") UUID assetId);

    @Query("""
            select coalesce(sum(t.realizedProfit), 0) from WalletTransaction t
            where t.walletId = :walletId and t.reversedAt is null
            """)
    BigDecimal sumRealizedProfit(@Param("walletId") UUID walletId);
}
