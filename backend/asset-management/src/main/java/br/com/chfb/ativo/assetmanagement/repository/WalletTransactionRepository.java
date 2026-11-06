package br.com.chfb.ativo.assetmanagement.repository;

import br.com.chfb.ativo.assetmanagement.entity.WalletTransaction;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {

    Optional<WalletTransaction> findByIdAndWalletId(UUID id, UUID walletId);

    long countByReversedAtIsNull();

    /** Linhas com o id do ativo e o número de operações, inclusive as estornadas. */
    @Query("select t.asset.id, count(t) from WalletTransaction t where t.asset.id in :assetIds group by t.asset.id")
    List<Object[]> countByAsset(@Param("assetIds") Collection<UUID> assetIds);

    /** Operações válidas de um ativo, na ordem em que aconteceram; no mesmo instante, na ordem em que foram lançadas. */
    @Query("""
            select t from WalletTransaction t
            where t.walletId = :walletId and t.asset.id = :assetId and t.reversedAt is null
            order by t.timestamp, t.recordedAt, t.id
            """)
    List<WalletTransaction> findActiveByWalletAndAsset(@Param("walletId") UUID walletId, @Param("assetId") UUID assetId);

    /** Operações válidas da carteira, na mesma ordem usada para refazer uma posição. */
    @Query("""
            select t from WalletTransaction t
            join fetch t.asset
            where t.walletId = :walletId and t.reversedAt is null
            order by t.timestamp, t.recordedAt, t.id
            """)
    List<WalletTransaction> findActiveByWallet(@Param("walletId") UUID walletId);

    List<WalletTransaction> findByWalletIdAndReversedAtIsNotNull(UUID walletId);

    @Query("""
            select coalesce(sum(t.realizedProfit), 0) from WalletTransaction t
            where t.walletId = :walletId and t.reversedAt is null
            """)
    BigDecimal sumRealizedProfit(@Param("walletId") UUID walletId);
}
