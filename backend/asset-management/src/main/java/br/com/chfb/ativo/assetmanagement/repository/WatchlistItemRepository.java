package br.com.chfb.ativo.assetmanagement.repository;

import br.com.chfb.ativo.assetmanagement.entity.Asset;
import br.com.chfb.ativo.assetmanagement.entity.WatchlistItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, UUID> {

    @Query("""
            select w from WatchlistItem w
            join fetch w.asset a
            where w.userId = :userId
            order by a.symbol
            """)
    List<WatchlistItem> findAllByUserId(@Param("userId") UUID userId);

    @Query("select w from WatchlistItem w join fetch w.asset where w.id = :id and w.userId = :userId")
    Optional<WatchlistItem> findOwned(@Param("id") UUID id, @Param("userId") UUID userId);

    long countByUserId(UUID userId);

    boolean existsByUserIdAndAssetId(UUID userId, UUID assetId);

    long countByAssetId(UUID assetId);

    /** Ativos acompanhados por qualquer usuário. */
    @Query("select distinct w.asset from WatchlistItem w")
    List<Asset> findWatchedAssets();
}
