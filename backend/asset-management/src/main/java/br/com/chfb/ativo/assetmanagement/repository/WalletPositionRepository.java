package br.com.chfb.ativo.assetmanagement.repository;

import br.com.chfb.ativo.assetmanagement.entity.Asset;
import br.com.chfb.ativo.assetmanagement.entity.WalletPosition;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletPositionRepository extends JpaRepository<WalletPosition, UUID> {

    Optional<WalletPosition> findByWalletIdAndAssetId(UUID walletId, UUID assetId);

    @Query("""
            select p from WalletPosition p
            join fetch p.asset a
            where p.walletId = :walletId and p.quantity > 0
            order by a.symbol
            """)
    List<WalletPosition> findActiveByWalletId(@Param("walletId") UUID walletId);

    /** Inclui as zeradas: recalcular uma posição pode reabri-la. */
    @Query("select p from WalletPosition p join fetch p.asset where p.walletId = :walletId")
    List<WalletPosition> findAllByWalletId(@Param("walletId") UUID walletId);

    /** Linhas com o id do ativo e o número de posições abertas. */
    @Query("""
            select p.asset.id, count(p) from WalletPosition p
            where p.quantity > 0 and p.asset.id in :assetIds
            group by p.asset.id
            """)
    List<Object[]> countOpenByAsset(@Param("assetIds") Collection<UUID> assetIds);

    @Query("select p from WalletPosition p join fetch p.asset where p.quantity > 0")
    List<WalletPosition> findAllActive();

    /** Ativos com posição aberta em qualquer carteira. */
    @Query("select distinct p.asset from WalletPosition p where p.quantity > 0")
    List<Asset> findActiveAssets();
}
