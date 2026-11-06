package br.com.chfb.ativo.assetmanagement.service;

import br.com.chfb.ativo.assetmanagement.dto.PortfolioHistoryPoint;
import br.com.chfb.ativo.assetmanagement.entity.PortfolioSnapshot;
import br.com.chfb.ativo.assetmanagement.entity.TransactionType;
import br.com.chfb.ativo.assetmanagement.entity.WalletPosition;
import br.com.chfb.ativo.assetmanagement.entity.WalletTransaction;
import br.com.chfb.ativo.assetmanagement.repository.WalletTransactionRepository;
import br.com.chfb.ativo.sharedkernel.market.PricePoint;
import br.com.chfb.ativo.sharedkernel.market.QuoteLookup;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Um snapshot é uma foto: o que foi estornado depois continua gravado nele. Os pontos tirados enquanto uma operação
 * hoje estornada ainda valia são refeitos na leitura, como se ela nunca tivesse existido. Os demais saem como foram
 * gravados, e a tabela não muda, o que também alcança estornos anteriores a esta correção.
 */
@Component
public class ReversalHistoryCorrector {

    private final WalletTransactionRepository transactionRepository;
    private final QuoteLookup quoteLookup;

    public ReversalHistoryCorrector(WalletTransactionRepository transactionRepository, QuoteLookup quoteLookup) {
        this.transactionRepository = transactionRepository;
        this.quoteLookup = quoteLookup;
    }

    /** @param snapshots em ordem de data */
    public List<PortfolioHistoryPoint> correct(UUID walletId, List<PortfolioSnapshot> snapshots) {
        List<WalletTransaction> reversed = transactionRepository.findByWalletIdAndReversedAtIsNotNull(walletId);
        List<Instant> affected = snapshots.stream()
                .map(PortfolioSnapshot::getSnapshotTime)
                .filter(time -> reversed.stream().anyMatch(transaction -> transaction.wasReversedAfter(time)))
                .toList();
        if (affected.isEmpty()) {
            return snapshots.stream().map(PortfolioHistoryPoint::from).toList();
        }

        Set<Instant> affectedTimes = Set.copyOf(affected);
        List<WalletTransaction> active = transactionRepository.findActiveByWallet(walletId);
        Map<String, NavigableMap<Instant, BigDecimal>> prices =
                priceTimelines(symbolsOf(active), affected.get(0), affected.get(affected.size() - 1));

        List<PortfolioHistoryPoint> points = new ArrayList<>();
        for (PortfolioSnapshot snapshot : snapshots) {
            if (!affectedTimes.contains(snapshot.getSnapshotTime())) {
                points.add(PortfolioHistoryPoint.from(snapshot));
                continue;
            }
            Collection<WalletPosition> open = positionsAt(walletId, snapshot.getSnapshotTime(), active);
            // Sem posição aberta não há ponto, como na geração dos snapshots.
            if (!open.isEmpty()) {
                points.add(value(snapshot.getSnapshotTime(), open, prices));
            }
        }
        return points;
    }

    private static Set<String> symbolsOf(List<WalletTransaction> transactions) {
        return transactions.stream().map(transaction -> transaction.getAsset().getSymbol()).collect(Collectors.toSet());
    }

    /** Em cada linha do tempo, o primeiro preço é o último conhecido em {@code from}. */
    private Map<String, NavigableMap<Instant, BigDecimal>> priceTimelines(Set<String> symbols, Instant from, Instant to) {
        Map<String, NavigableMap<Instant, BigDecimal>> timelines = new HashMap<>();
        quoteLookup.findPricesAt(symbols, from).forEach((symbol, price) ->
                timelines.computeIfAbsent(symbol, key -> new TreeMap<>()).put(from, price));
        quoteLookup.findPricesBetween(symbols, from, to).forEach((symbol, points) -> {
            NavigableMap<Instant, BigDecimal> timeline = timelines.computeIfAbsent(symbol, key -> new TreeMap<>());
            for (PricePoint point : points) {
                timeline.put(point.time(), point.price());
            }
        });
        return timelines;
    }

    /**
     * Refaz as posições só com o que já estava lançado em {@code time}. Uma venda pode ter dependido de uma compra
     * estornada e coberta por outra lançada mais tarde; naquele instante, ela só tira o que há em custódia.
     */
    private static Collection<WalletPosition> positionsAt(UUID walletId, Instant time, List<WalletTransaction> active) {
        Map<String, WalletPosition> positions = new LinkedHashMap<>();
        for (WalletTransaction transaction : active) {
            if (!transaction.wasRecordedBy(time)) {
                continue;
            }
            WalletPosition position = positions.computeIfAbsent(
                    transaction.getAsset().getSymbol(),
                    symbol -> new WalletPosition(walletId, transaction.getAsset()));
            if (transaction.getType() == TransactionType.BUY) {
                position.addPurchase(transaction.getQuantity(), transaction.getUnitPrice(), transaction.getFee());
            } else {
                position.removeSold(transaction.getQuantity().min(position.getQuantity()));
            }
        }
        return positions.values().stream().filter(position -> position.getQuantity().signum() > 0).toList();
    }

    /** Sem preço conhecido até ali, a posição vale o custo, como em {@link PortfolioValuation}. */
    private static PortfolioHistoryPoint value(
            Instant time, Collection<WalletPosition> positions, Map<String, NavigableMap<Instant, BigDecimal>> prices) {
        BigDecimal invested = BigDecimal.ZERO;
        BigDecimal market = BigDecimal.ZERO;
        for (WalletPosition position : positions) {
            BigDecimal cost = PortfolioValuation.money(position.getQuantity().multiply(position.getAveragePrice()));
            NavigableMap<Instant, BigDecimal> timeline = prices.get(position.getAsset().getSymbol());
            Map.Entry<Instant, BigDecimal> price = timeline == null ? null : timeline.floorEntry(time);
            invested = invested.add(cost);
            market = market.add(price == null ? cost : PortfolioValuation.money(position.getQuantity().multiply(price.getValue())));
        }
        return new PortfolioHistoryPoint(time, invested, market);
    }
}
