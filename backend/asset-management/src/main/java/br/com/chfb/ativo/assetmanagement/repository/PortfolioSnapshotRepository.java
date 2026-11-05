package br.com.chfb.ativo.assetmanagement.repository;

import br.com.chfb.ativo.assetmanagement.entity.PortfolioSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioSnapshotRepository extends JpaRepository<PortfolioSnapshot, UUID> {

    List<PortfolioSnapshot> findByWalletIdAndSnapshotTimeGreaterThanEqualOrderBySnapshotTime(UUID walletId, Instant from);
}
