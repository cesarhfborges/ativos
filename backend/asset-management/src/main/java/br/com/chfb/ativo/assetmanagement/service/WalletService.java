package br.com.chfb.ativo.assetmanagement.service;

import br.com.chfb.ativo.assetmanagement.dto.AssetResponse;
import br.com.chfb.ativo.assetmanagement.dto.BuyOrder;
import br.com.chfb.ativo.assetmanagement.dto.RebuildResult;
import br.com.chfb.ativo.assetmanagement.dto.SellOrder;
import br.com.chfb.ativo.assetmanagement.dto.StatementFilter;
import br.com.chfb.ativo.assetmanagement.dto.StatementPage;
import br.com.chfb.ativo.assetmanagement.dto.TransactionResponse;
import br.com.chfb.ativo.assetmanagement.entity.Amounts;
import br.com.chfb.ativo.assetmanagement.entity.Asset;
import br.com.chfb.ativo.assetmanagement.entity.TransactionType;
import br.com.chfb.ativo.assetmanagement.entity.VerificationStatus;
import br.com.chfb.ativo.assetmanagement.entity.Wallet;
import br.com.chfb.ativo.assetmanagement.entity.WalletPosition;
import br.com.chfb.ativo.assetmanagement.entity.WalletTransaction;
import br.com.chfb.ativo.assetmanagement.exception.BusinessRuleException;
import br.com.chfb.ativo.assetmanagement.exception.TransactionNotFoundException;
import br.com.chfb.ativo.assetmanagement.repository.AssetRepository;
import br.com.chfb.ativo.assetmanagement.repository.StatementQuery;
import br.com.chfb.ativo.assetmanagement.repository.WalletPositionRepository;
import br.com.chfb.ativo.assetmanagement.repository.WalletRepository;
import br.com.chfb.ativo.assetmanagement.repository.WalletTransactionRepository;
import br.com.chfb.ativo.sharedkernel.market.SymbolTrackedEvent;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Toda operação recebe o id do usuário logado e só alcança a carteira dele. */
@Service
public class WalletService {

    private static final int ASSET_SEARCH_LIMIT = 10;
    private static final String DEFAULT_WALLET_NAME = "Carteira principal";
    private static final String INSUFFICIENT_POSITION =
            "Quantidade em custódia insuficiente na data desta venda ou de uma venda posterior";

    private final WalletRepository walletRepository;
    private final AssetRepository assetRepository;
    private final WalletPositionRepository positionRepository;
    private final WalletTransactionRepository transactionRepository;
    private final StatementQuery statementQuery;
    private final AssetRegistrar assetRegistrar;
    private final TransactionTemplate transactions;
    private final ApplicationEventPublisher events;

    public WalletService(
            WalletRepository walletRepository,
            AssetRepository assetRepository,
            WalletPositionRepository positionRepository,
            WalletTransactionRepository transactionRepository,
            StatementQuery statementQuery,
            AssetRegistrar assetRegistrar,
            PlatformTransactionManager transactionManager,
            ApplicationEventPublisher events) {
        this.events = events;
        this.statementQuery = statementQuery;
        this.assetRegistrar = assetRegistrar;
        this.transactions = new TransactionTemplate(transactionManager);
        this.walletRepository = walletRepository;
        this.assetRepository = assetRepository;
        this.positionRepository = positionRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public StatementPage statement(UUID userId, StatementFilter filter, int page, int perPage) {
        return walletRepository.findByUserId(userId)
                .map(wallet -> {
                    List<WalletTransaction> rows = statementQuery.find(wallet.getId(), filter, (page - 1) * perPage, perPage + 1);
                    return new StatementPage(
                            rows.stream().limit(perPage).map(TransactionResponse::from).toList(),
                            rows.size() > perPage);
                })
                .orElseGet(() -> new StatementPage(List.of(), false));
    }

    @Transactional(readOnly = true)
    public List<AssetResponse> searchAssets(String query) {
        String term = query.strip().toUpperCase(Locale.ROOT).replaceAll("([!%_])", "!$1");
        return assetRepository.search(term + "%", "%" + term + "%", PageRequest.of(0, ASSET_SEARCH_LIMIT)).stream()
                .map(AssetResponse::from)
                .toList();
    }

    /** O provedor é consultado antes da transação: uma API lenta não pode segurar a trava da carteira. */
    public TransactionResponse buy(UUID userId, BuyOrder order) {
        VerificationStatus status = assetRegistrar.verifyNew(order.symbol(), order.assetType(), order.acceptUnverified());
        return transactions.execute(transaction -> registerBuy(userId, order, status));
    }

    private TransactionResponse registerBuy(UUID userId, BuyOrder order, VerificationStatus status) {
        Wallet wallet = lockWallet(userId);
        BigDecimal quantity = Amounts.quantity(order.quantity());
        BigDecimal unitPrice = Amounts.price(order.unitPrice());
        BigDecimal fee = Amounts.money(order.fee());
        BigDecimal total = Amounts.money(quantity.multiply(unitPrice).add(fee));

        Asset asset = assetRegistrar.findOrRegister(order.symbol(), order.name(), order.assetType(), status);
        if (positionRepository.findByWalletIdAndAssetId(wallet.getId(), asset.getId()).isEmpty()) {
            positionRepository.save(new WalletPosition(wallet.getId(), asset));
        }
        WalletTransaction transaction = transactionRepository.save(WalletTransaction.buy(
                wallet.getId(), asset, quantity, unitPrice, fee, total, order.executedAt()));

        rebuildPosition(wallet, asset, INSUFFICIENT_POSITION);
        events.publishEvent(new SymbolTrackedEvent(asset.getSymbol(), asset.getAssetType().toAssetClass()));
        return result(transaction);
    }

    @Transactional
    public TransactionResponse sell(UUID userId, SellOrder order) {
        Wallet wallet = lockWallet(userId);
        BigDecimal quantity = Amounts.quantity(order.quantity());
        BigDecimal unitPrice = Amounts.price(order.unitPrice());
        BigDecimal fee = Amounts.money(order.fee());

        // Ativo desconhecido e posição inexistente recebem a mesma resposta de custódia insuficiente.
        Asset asset = assetRepository.findBySymbol(AssetRegistrar.normalize(order.symbol()))
                .orElseThrow(() -> new BusinessRuleException(INSUFFICIENT_POSITION));
        WalletPosition position = positionRepository.findByWalletIdAndAssetId(wallet.getId(), asset.getId())
                .orElseThrow(() -> new BusinessRuleException(INSUFFICIENT_POSITION));

        BigDecimal net = quantity.multiply(unitPrice).subtract(fee);
        if (net.signum() < 0) {
            throw new BusinessRuleException("A taxa não pode ser maior que o valor da venda");
        }
        WalletTransaction transaction = transactionRepository.save(WalletTransaction.sell(
                wallet.getId(),
                asset,
                quantity,
                unitPrice,
                fee,
                Amounts.money(net),
                realizedProfit(quantity, unitPrice, fee, position.getAveragePrice()),
                order.executedAt()));

        rebuildPosition(wallet, asset, INSUFFICIENT_POSITION);
        return result(transaction);
    }

    /** O estorno não apaga a operação: ela só deixa de contar quando a posição é refeita. */
    @Transactional
    public TransactionResponse reverse(UUID userId, UUID transactionId) {
        Wallet wallet = lockWallet(userId);
        WalletTransaction transaction = transactionRepository.findByIdAndWalletId(transactionId, wallet.getId())
                .orElseThrow(TransactionNotFoundException::new);
        if (transaction.isReversed()) {
            throw new BusinessRuleException("Esta operação já foi estornada");
        }
        transaction.markReversed();

        Asset asset = transaction.getAsset();
        rebuildPosition(
                wallet,
                asset,
                "Não é possível estornar: uma venda posterior de %s ficaria sem quantidade em custódia. "
                                .formatted(asset.getSymbol())
                        + "Estorne essa venda antes.");
        return result(transaction);
    }

    /** Refaz todas as posições de todas as carteiras, depois de uma mudança na regra de cálculo. */
    @Transactional
    public RebuildResult rebuildAllPositions() {
        int walletCount = 0;
        int positionCount = 0;
        for (Wallet wallet : walletRepository.findAll()) {
            walletRepository.findByUserIdForUpdate(wallet.getUserId());
            for (WalletPosition position : positionRepository.findAllByWalletId(wallet.getId())) {
                rebuildPosition(
                        wallet,
                        position.getAsset(),
                        "Uma venda de %s ficaria sem quantidade em custódia".formatted(position.getAsset().getSymbol()));
                positionCount++;
            }
            walletCount++;
        }
        return new RebuildResult(walletCount, positionCount);
    }

    /**
     * Refaz a posição a partir das operações válidas, em ordem de data. Uma operação no meio do histórico
     * muda o preço médio e o lucro de tudo o que veio depois, e não pode deixar uma venda sem custódia.
     */
    private void rebuildPosition(Wallet wallet, Asset asset, String shortageMessage) {
        WalletPosition recalculated = new WalletPosition(wallet.getId(), asset);
        for (WalletTransaction active : transactionRepository.findActiveByWalletAndAsset(wallet.getId(), asset.getId())) {
            if (active.getType() == TransactionType.BUY) {
                recalculated.addPurchase(active.getQuantity(), active.getUnitPrice(), active.getFee());
            } else {
                if (!recalculated.holds(active.getQuantity())) {
                    throw new BusinessRuleException(shortageMessage);
                }
                active.updateRealizedProfit(realizedProfit(
                        active.getQuantity(), active.getUnitPrice(), active.getFee(), recalculated.getAveragePrice()));
                recalculated.removeSold(active.getQuantity());
            }
        }
        positionRepository.findByWalletIdAndAssetId(wallet.getId(), asset.getId())
                .orElseThrow()
                .replaceWith(recalculated);
    }

    /** A carteira nasce na primeira operação do usuário. */
    private Wallet lockWallet(UUID userId) {
        return walletRepository.findByUserIdForUpdate(userId).orElseGet(() -> {
            walletRepository.insertIfAbsent(UUID.randomUUID(), userId, DEFAULT_WALLET_NAME);
            return walletRepository.findByUserIdForUpdate(userId).orElseThrow();
        });
    }

    /** Valor líquido da venda menos o custo da quantidade vendida. */
    private static BigDecimal realizedProfit(
            BigDecimal quantity, BigDecimal unitPrice, BigDecimal fee, BigDecimal averagePrice) {
        return Amounts.money(quantity.multiply(unitPrice).subtract(fee).subtract(quantity.multiply(averagePrice)));
    }

    private TransactionResponse result(WalletTransaction transaction) {
        // Grava agora para que uma violação de restrição do banco aconteça antes de responder.
        transactionRepository.flush();
        return TransactionResponse.from(transaction);
    }
}
