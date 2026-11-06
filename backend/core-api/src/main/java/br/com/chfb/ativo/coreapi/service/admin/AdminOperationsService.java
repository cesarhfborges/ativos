package br.com.chfb.ativo.coreapi.service.admin;

import br.com.chfb.ativo.assetmanagement.dto.AssetCatalogEntry;
import br.com.chfb.ativo.assetmanagement.dto.AssetVerification;
import br.com.chfb.ativo.assetmanagement.dto.RebuildResult;
import br.com.chfb.ativo.assetmanagement.entity.AssetType;
import br.com.chfb.ativo.assetmanagement.repository.WalletRepository;
import br.com.chfb.ativo.assetmanagement.repository.WalletTransactionRepository;
import br.com.chfb.ativo.assetmanagement.service.AssetCatalogService;
import br.com.chfb.ativo.assetmanagement.service.WalletService;
import br.com.chfb.ativo.coreapi.dto.admin.AdminOverviewResponse;
import br.com.chfb.ativo.coreapi.entity.Role;
import br.com.chfb.ativo.coreapi.repository.UserRepository;
import br.com.chfb.ativo.markettracker.service.QuoteCollector;
import br.com.chfb.ativo.sharedkernel.market.ActiveSymbols;
import br.com.chfb.ativo.sharedkernel.market.ActiveSymbolsProvider;
import br.com.chfb.ativo.sharedkernel.market.Quote;
import br.com.chfb.ativo.sharedkernel.market.QuoteLookup;
import br.com.chfb.ativo.sharedkernel.settings.AppSettings;
import br.com.chfb.ativo.sharedkernel.settings.SettingKeys;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Visão geral e ações sobre o sistema, cada uma gravada na auditoria na mesma transação. */
@Service
public class AdminOperationsService {

    /** A coleta é horária: passou disso, ao menos uma coleta falhou para o símbolo. */
    private static final Duration STALE_AFTER = Duration.ofHours(2);

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final WalletService walletService;
    private final AssetCatalogService assetCatalogService;
    private final QuoteCollector quoteCollector;
    private final ActiveSymbolsProvider activeSymbolsProvider;
    private final QuoteLookup quoteLookup;
    private final AppSettings appSettings;
    private final AdminAuditService audit;

    public AdminOperationsService(
            UserRepository userRepository,
            WalletRepository walletRepository,
            WalletTransactionRepository transactionRepository,
            WalletService walletService,
            AssetCatalogService assetCatalogService,
            QuoteCollector quoteCollector,
            ActiveSymbolsProvider activeSymbolsProvider,
            QuoteLookup quoteLookup,
            AppSettings appSettings,
            AdminAuditService audit) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.walletService = walletService;
        this.assetCatalogService = assetCatalogService;
        this.quoteCollector = quoteCollector;
        this.activeSymbolsProvider = activeSymbolsProvider;
        this.quoteLookup = quoteLookup;
        this.appSettings = appSettings;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public AdminOverviewResponse overview() {
        ActiveSymbols active = activeSymbolsProvider.activeSymbols();
        TreeSet<String> symbols = new TreeSet<>(active.crypto());
        symbols.addAll(active.stocks());
        Map<String, Quote> quotes = quoteLookup.findBySymbols(symbols);
        Instant staleBefore = Instant.now().minus(STALE_AFTER);
        List<String> withoutQuote = symbols.stream().filter(symbol -> !quotes.containsKey(symbol)).toList();
        List<String> stale = symbols.stream()
                .filter(symbol -> quotes.containsKey(symbol) && quotes.get(symbol).lastUpdatedAt().isBefore(staleBefore))
                .toList();

        return new AdminOverviewResponse(
                userRepository.count(),
                userRepository.countByActiveTrue(),
                userRepository.countByRoleAndActiveTrue(Role.ADMIN),
                walletRepository.count(),
                transactionRepository.countByReversedAtIsNull(),
                appSettings.current().getBoolean(SettingKeys.COLLECTION_ENABLED),
                quoteCollector.lastCollectedAt(),
                symbols.size(),
                withoutQuote,
                stale);
    }

    /** Fora de transação: a coleta chama APIs externas. */
    public boolean collectQuotes(String actorEmail) {
        boolean refreshed = quoteCollector.refresh();
        if (refreshed) {
            audit.record(actorEmail, "QUOTES_COLLECTED", null, null);
        }
        return refreshed;
    }

    @Transactional
    public RebuildResult rebuildPositions(String actorEmail) {
        RebuildResult result = walletService.rebuildAllPositions();
        audit.record(
                actorEmail,
                "POSITIONS_REBUILT",
                null,
                "%d carteiras, %d posições".formatted(result.wallets(), result.positions()));
        return result;
    }

    @Transactional
    public AssetCatalogEntry updateAsset(UUID id, String name, AssetType assetType, String actorEmail) {
        AssetCatalogEntry entry = assetCatalogService.update(id, name, assetType);
        audit.record(actorEmail, "ASSET_UPDATED", entry.symbol(), "Tipo: %s; nome: %s".formatted(entry.assetType(), entry.name()));
        return entry;
    }

    @Transactional
    public AssetVerification verifyAsset(UUID id, String actorEmail) {
        AssetVerification verification = assetCatalogService.verify(id);
        audit.record(actorEmail, "ASSET_VERIFIED", verification.asset().symbol(), "Resultado: " + verification.check());
        return verification;
    }

    @Transactional
    public void removeAsset(UUID id, String actorEmail) {
        audit.record(actorEmail, "ASSET_REMOVED", assetCatalogService.remove(id), null);
    }
}
