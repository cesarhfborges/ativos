package br.com.chfb.ativo.assetmanagement.repository;

import br.com.chfb.ativo.assetmanagement.dto.StatementFilter;
import br.com.chfb.ativo.assetmanagement.entity.Asset;
import br.com.chfb.ativo.assetmanagement.entity.TransactionStatus;
import br.com.chfb.ativo.assetmanagement.entity.WalletTransaction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Monta o extrato só com os filtros informados, sem {@code COUNT}: quem chama pede uma linha a mais para saber se há outra página. */
@Repository
public class StatementQuery {

    private static final char LIKE_ESCAPE = '!';

    private final EntityManager entityManager;

    public StatementQuery(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<WalletTransaction> find(UUID walletId, StatementFilter filter, int offset, int limit) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<WalletTransaction> query = builder.createQuery(WalletTransaction.class);
        Root<WalletTransaction> transaction = query.from(WalletTransaction.class);
        @SuppressWarnings("unchecked")
        Join<WalletTransaction, Asset> asset = (Join<WalletTransaction, Asset>) transaction.<WalletTransaction, Asset>fetch("asset", JoinType.INNER);

        query.where(predicates(builder, transaction, asset, walletId, filter).toArray(Predicate[]::new));
        query.orderBy(
                builder.desc(transaction.get("timestamp")),
                builder.desc(transaction.get("recordedAt")),
                builder.desc(transaction.get("id")));
        return entityManager.createQuery(query).setFirstResult(offset).setMaxResults(limit).getResultList();
    }

    private static List<Predicate> predicates(
            CriteriaBuilder builder,
            Root<WalletTransaction> transaction,
            Join<WalletTransaction, Asset> asset,
            UUID walletId,
            StatementFilter filter) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(builder.equal(transaction.get("walletId"), walletId));
        if (filter.asset() != null && !filter.asset().isBlank()) {
            String term = "%" + filter.asset().strip().toUpperCase(Locale.ROOT).replaceAll("([!%_])", "!$1") + "%";
            predicates.add(builder.or(
                    builder.like(builder.upper(asset.get("symbol")), term, LIKE_ESCAPE),
                    builder.like(builder.upper(asset.get("name")), term, LIKE_ESCAPE)));
        }
        if (filter.type() != null) {
            predicates.add(builder.equal(transaction.get("type"), filter.type()));
        }
        if (filter.assetType() != null) {
            predicates.add(builder.equal(asset.get("assetType"), filter.assetType()));
        }
        if (filter.status() != null) {
            predicates.add(filter.status() == TransactionStatus.REVERSED
                    ? builder.isNotNull(transaction.get("reversedAt"))
                    : builder.isNull(transaction.get("reversedAt")));
        }
        if (filter.from() != null) {
            predicates.add(builder.greaterThanOrEqualTo(transaction.get("timestamp"), filter.from()));
        }
        if (filter.to() != null) {
            predicates.add(builder.lessThanOrEqualTo(transaction.get("timestamp"), filter.to()));
        }
        if (filter.minTotal() != null) {
            predicates.add(builder.greaterThanOrEqualTo(transaction.get("totalAmount"), filter.minTotal()));
        }
        if (filter.maxTotal() != null) {
            predicates.add(builder.lessThanOrEqualTo(transaction.get("totalAmount"), filter.maxTotal()));
        }
        return predicates;
    }
}
