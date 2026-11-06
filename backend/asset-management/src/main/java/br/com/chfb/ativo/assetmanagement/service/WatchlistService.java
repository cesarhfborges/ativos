package br.com.chfb.ativo.assetmanagement.service;

import br.com.chfb.ativo.assetmanagement.dto.MarketAssetResponse;
import br.com.chfb.ativo.assetmanagement.dto.WatchlistItemOrder;
import br.com.chfb.ativo.assetmanagement.dto.WatchlistItemResponse;
import br.com.chfb.ativo.assetmanagement.dto.WatchlistPlan;
import br.com.chfb.ativo.assetmanagement.entity.Asset;
import br.com.chfb.ativo.assetmanagement.entity.VerificationStatus;
import br.com.chfb.ativo.assetmanagement.entity.WalletPosition;
import br.com.chfb.ativo.assetmanagement.entity.WatchlistItem;
import br.com.chfb.ativo.assetmanagement.exception.AlreadyWatchedException;
import br.com.chfb.ativo.assetmanagement.exception.BusinessRuleException;
import br.com.chfb.ativo.assetmanagement.exception.WatchlistItemNotFoundException;
import br.com.chfb.ativo.assetmanagement.repository.WalletPositionRepository;
import br.com.chfb.ativo.assetmanagement.repository.WalletRepository;
import br.com.chfb.ativo.assetmanagement.repository.WatchlistItemRepository;
import br.com.chfb.ativo.sharedkernel.market.Quote;
import br.com.chfb.ativo.sharedkernel.market.QuoteLookup;
import br.com.chfb.ativo.sharedkernel.market.SymbolTrackedEvent;
import br.com.chfb.ativo.sharedkernel.market.TechnicalSignal;
import br.com.chfb.ativo.sharedkernel.market.TechnicalSignals;
import br.com.chfb.ativo.sharedkernel.settings.AppSettings;
import br.com.chfb.ativo.sharedkernel.settings.SettingKeys;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Toda operação recebe o id do usuário logado e só alcança os itens dele. Acompanhar nunca altera a carteira. */
@Service
public class WatchlistService {

    private final WatchlistItemRepository repository;
    private final WalletRepository walletRepository;
    private final WalletPositionRepository positionRepository;
    private final MarketOverviewService marketOverview;
    private final QuoteLookup quoteLookup;
    private final AssetRegistrar assetRegistrar;
    private final AppSettings appSettings;
    private final ApplicationEventPublisher events;
    private final Optional<TechnicalSignals> technicalSignals;
    private final TransactionTemplate transactions;

    public WatchlistService(
            WatchlistItemRepository repository,
            WalletRepository walletRepository,
            WalletPositionRepository positionRepository,
            MarketOverviewService marketOverview,
            QuoteLookup quoteLookup,
            AssetRegistrar assetRegistrar,
            AppSettings appSettings,
            ApplicationEventPublisher events,
            Optional<TechnicalSignals> technicalSignals,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.walletRepository = walletRepository;
        this.positionRepository = positionRepository;
        this.marketOverview = marketOverview;
        this.quoteLookup = quoteLookup;
        this.assetRegistrar = assetRegistrar;
        this.appSettings = appSettings;
        this.events = events;
        this.technicalSignals = technicalSignals;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public List<WatchlistItemResponse> list(UUID userId, int days) {
        List<WatchlistItem> items = repository.findAllByUserId(userId);
        return responses(userId, items, days);
    }

    /** O provedor é consultado antes da transação: uma API lenta não pode segurar conexões. */
    public WatchlistItemResponse add(UUID userId, WatchlistItemOrder order) {
        validate(order.plan());
        VerificationStatus status =
                assetRegistrar.verifyNew(order.symbol(), order.assetType(), order.acceptUnverified());
        try {
            return transactions.execute(transaction -> register(userId, order, status));
        } catch (DataIntegrityViolationException e) {
            // Dois pedidos simultâneos: a restrição do banco barra o segundo.
            throw new AlreadyWatchedException(AssetRegistrar.normalize(order.symbol()));
        }
    }

    @Transactional
    public WatchlistItemResponse update(UUID userId, UUID id, WatchlistPlan plan) {
        validate(plan);
        WatchlistItem item = find(userId, id);
        apply(item, plan);
        return responses(userId, List.of(item), 1).get(0);
    }

    @Transactional
    public void remove(UUID userId, UUID id) {
        repository.delete(find(userId, id));
    }

    private WatchlistItemResponse register(UUID userId, WatchlistItemOrder order, VerificationStatus status) {
        int limit = appSettings.current().getInt(SettingKeys.WATCHLIST_MAX_ITEMS);
        if (repository.countByUserId(userId) >= limit) {
            throw new BusinessRuleException("Você atingiu o limite de %d ativos acompanhados".formatted(limit));
        }
        Asset asset = assetRegistrar.findOrRegister(order.symbol(), order.name(), order.assetType(), status);
        if (repository.existsByUserIdAndAssetId(userId, asset.getId())) {
            throw new AlreadyWatchedException(asset.getSymbol());
        }
        WatchlistItem item = new WatchlistItem(userId, asset, Instant.now());
        apply(item, order.plan());
        repository.saveAndFlush(item);
        events.publishEvent(new SymbolTrackedEvent(asset.getSymbol(), asset.getAssetType().toAssetClass()));
        return responses(userId, List.of(item), 1).get(0);
    }

    private static void apply(WatchlistItem item, WatchlistPlan plan) {
        String note = plan.note() == null || plan.note().isBlank() ? null : plan.note().strip();
        item.changePlan(plan.buyBelow(), plan.sellAbove(), plan.stopLoss(), note, Instant.now());
    }

    /** O stop pode ficar acima do preço de compra, para proteger um lucro. Os limites são sempre em relação à venda. */
    private static void validate(WatchlistPlan plan) {
        BigDecimal sell = plan.sellAbove();
        if (sell == null) {
            return;
        }
        if (plan.buyBelow() != null && plan.buyBelow().compareTo(sell) >= 0) {
            throw new BusinessRuleException("O alvo de venda precisa ser maior que o preço de compra");
        }
        if (plan.stopLoss() != null && plan.stopLoss().compareTo(sell) >= 0) {
            throw new BusinessRuleException("O alvo de venda precisa ser maior que o stop");
        }
    }

    private WatchlistItem find(UUID userId, UUID id) {
        return repository.findOwned(id, userId).orElseThrow(WatchlistItemNotFoundException::new);
    }

    private List<WatchlistItemResponse> responses(UUID userId, List<WatchlistItem> items, int days) {
        List<String> symbols = items.stream().map(item -> item.getAsset().getSymbol()).toList();
        Map<String, Quote> quotes = quoteLookup.findBySymbols(symbols);
        Map<String, MarketAssetResponse> market = marketOverview.bySymbols(symbols, days);
        Map<String, TechnicalSignal> signals = technicalSignals.map(service -> service.forSymbols(symbols)).orElseGet(Map::of);
        Map<UUID, WalletPosition> positions = walletRepository.findByUserId(userId)
                .map(wallet -> positionRepository.findActiveByWalletId(wallet.getId()).stream()
                        .collect(Collectors.toMap(position -> position.getAsset().getId(), Function.identity())))
                .orElseGet(Map::of);
        return items.stream()
                .map(item -> response(
                        item,
                        quotes.get(item.getAsset().getSymbol()),
                        market.get(item.getAsset().getSymbol()),
                        positions.get(item.getAsset().getId()),
                        signals.get(item.getAsset().getSymbol())))
                .toList();
    }

    private static WatchlistItemResponse response(
            WatchlistItem item, Quote quote, MarketAssetResponse market, WalletPosition position, TechnicalSignal signal) {
        Asset asset = item.getAsset();
        return new WatchlistItemResponse(
                item.getId(),
                asset.getSymbol(),
                asset.getName(),
                asset.getAssetType(),
                item.getBuyBelow(),
                item.getSellAbove(),
                item.getStopLoss(),
                item.getNote(),
                quote == null ? null : quote.price(),
                quote == null ? null : quote.lastUpdatedAt(),
                market.change24hPercent(),
                market.periodChangePercent(),
                market.periodLow(),
                market.periodHigh(),
                market.prices(),
                position == null ? BigDecimal.ZERO : position.getQuantity(),
                position == null ? null : position.getAveragePrice(),
                item.getCreatedAt(),
                signal);
    }
}
