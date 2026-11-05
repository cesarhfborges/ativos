package br.com.chfb.ativo.assetmanagement.service;

import br.com.chfb.ativo.assetmanagement.dto.BuyOrder;
import br.com.chfb.ativo.assetmanagement.dto.SellOrder;
import br.com.chfb.ativo.assetmanagement.dto.TransactionResponse;
import br.com.chfb.ativo.assetmanagement.entity.Amounts;
import br.com.chfb.ativo.assetmanagement.entity.Asset;
import br.com.chfb.ativo.assetmanagement.entity.TransactionType;
import br.com.chfb.ativo.assetmanagement.entity.Wallet;
import br.com.chfb.ativo.assetmanagement.entity.WalletPosition;
import br.com.chfb.ativo.assetmanagement.entity.WalletTransaction;
import br.com.chfb.ativo.assetmanagement.exception.AssetTypeConflictException;
import br.com.chfb.ativo.assetmanagement.exception.BusinessRuleException;
import br.com.chfb.ativo.assetmanagement.repository.AssetRepository;
import br.com.chfb.ativo.assetmanagement.repository.WalletPositionRepository;
import br.com.chfb.ativo.assetmanagement.repository.WalletRepository;
import br.com.chfb.ativo.assetmanagement.repository.WalletTransactionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Toda operação recebe o id do usuário logado e só alcança a carteira dele. */
@Service
public class WalletService {

    private static final String DEFAULT_WALLET_NAME = "Carteira principal";
    private static final String INSUFFICIENT_POSITION = "Quantidade em custódia insuficiente";

    private final WalletRepository walletRepository;
    private final AssetRepository assetRepository;
    private final WalletPositionRepository positionRepository;
    private final WalletTransactionRepository transactionRepository;

    public WalletService(
            WalletRepository walletRepository,
            AssetRepository assetRepository,
            WalletPositionRepository positionRepository,
            WalletTransactionRepository transactionRepository) {
        this.walletRepository = walletRepository;
        this.assetRepository = assetRepository;
        this.positionRepository = positionRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> statement(UUID userId, TransactionType type, int page, int size) {
        return walletRepository.findByUserId(userId)
                .map(wallet -> transactionRepository.findStatement(wallet.getId(), type, PageRequest.of(page, size)).stream()
                        .map(TransactionResponse::from)
                        .toList())
                .orElseGet(List::of);
    }

    @Transactional
    public TransactionResponse buy(UUID userId, BuyOrder order) {
        Wallet wallet = lockWallet(userId);
        BigDecimal quantity = Amounts.quantity(order.quantity());
        BigDecimal unitPrice = Amounts.money(order.unitPrice());
        BigDecimal fee = Amounts.money(order.fee());
        BigDecimal total = Amounts.money(quantity.multiply(unitPrice).add(fee));

        Asset asset = findOrRegisterAsset(order);
        WalletPosition position = positionRepository.findByWalletIdAndAssetId(wallet.getId(), asset.getId())
                .orElseGet(() -> new WalletPosition(wallet.getId(), asset));
        position.addPurchase(quantity, unitPrice);
        positionRepository.save(position);

        WalletTransaction transaction = transactionRepository.save(
                WalletTransaction.buy(wallet.getId(), asset, quantity, unitPrice, fee, total));
        return result(transaction);
    }

    @Transactional
    public TransactionResponse sell(UUID userId, SellOrder order) {
        Wallet wallet = lockWallet(userId);
        BigDecimal quantity = Amounts.quantity(order.quantity());
        BigDecimal unitPrice = Amounts.money(order.unitPrice());
        BigDecimal fee = Amounts.money(order.fee());

        // Ativo desconhecido e posição inexistente recebem a mesma resposta de custódia insuficiente.
        Asset asset = assetRepository.findBySymbol(normalizeSymbol(order.symbol()))
                .orElseThrow(() -> new BusinessRuleException(INSUFFICIENT_POSITION));
        WalletPosition position = positionRepository.findByWalletIdAndAssetId(wallet.getId(), asset.getId())
                .orElseThrow(() -> new BusinessRuleException(INSUFFICIENT_POSITION));

        BigDecimal net = quantity.multiply(unitPrice).subtract(fee);
        if (net.signum() < 0) {
            throw new BusinessRuleException("A taxa não pode ser maior que o valor da venda");
        }
        BigDecimal total = Amounts.money(net);

        position.removeSold(quantity);

        WalletTransaction transaction = transactionRepository.save(
                WalletTransaction.sell(wallet.getId(), asset, quantity, unitPrice, fee, total));
        return result(transaction);
    }

    /** A carteira nasce na primeira operação do usuário. */
    private Wallet lockWallet(UUID userId) {
        return walletRepository.findByUserIdForUpdate(userId).orElseGet(() -> {
            walletRepository.insertIfAbsent(UUID.randomUUID(), userId, DEFAULT_WALLET_NAME);
            return walletRepository.findByUserIdForUpdate(userId).orElseThrow();
        });
    }

    private Asset findOrRegisterAsset(BuyOrder order) {
        String symbol = normalizeSymbol(order.symbol());
        Asset asset = assetRepository.findBySymbol(symbol).orElseGet(() -> {
            String name = order.name() == null || order.name().isBlank() ? symbol : order.name().strip();
            assetRepository.insertIfAbsent(UUID.randomUUID(), symbol, name, order.assetType().name());
            return assetRepository.findBySymbol(symbol).orElseThrow();
        });
        if (asset.getAssetType() != order.assetType()) {
            throw new AssetTypeConflictException(symbol, asset.getAssetType());
        }
        return asset;
    }

    private static String normalizeSymbol(String symbol) {
        return symbol.toUpperCase(Locale.ROOT);
    }

    private TransactionResponse result(WalletTransaction transaction) {
        // Grava agora para que uma violação de restrição do banco aconteça antes de responder.
        transactionRepository.flush();
        return TransactionResponse.from(transaction);
    }
}
