package br.com.chfb.ativo.assetmanagement.repository;

import br.com.chfb.ativo.assetmanagement.entity.TransactionType;
import br.com.chfb.ativo.assetmanagement.entity.WalletTransaction;
import java.util.List;
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
            order by t.timestamp desc, t.id desc
            """)
    List<WalletTransaction> findStatement(
            @Param("walletId") UUID walletId, @Param("type") TransactionType type, Pageable pageable);
}
