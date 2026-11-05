package br.com.chfb.ativo.assetmanagement.repository;

import br.com.chfb.ativo.assetmanagement.entity.Asset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetRepository extends JpaRepository<Asset, UUID> {

    Optional<Asset> findBySymbol(String symbol);

    @Modifying
    @Query(
            value = """
                    INSERT INTO assets (id, symbol, name, asset_type)
                    VALUES (:id, :symbol, :name, :assetType)
                    ON CONFLICT (symbol) DO NOTHING
                    """,
            nativeQuery = true)
    void insertIfAbsent(
            @Param("id") UUID id,
            @Param("symbol") String symbol,
            @Param("name") String name,
            @Param("assetType") String assetType);
}
