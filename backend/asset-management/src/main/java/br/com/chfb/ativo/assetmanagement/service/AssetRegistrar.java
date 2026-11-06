package br.com.chfb.ativo.assetmanagement.service;

import br.com.chfb.ativo.assetmanagement.entity.Asset;
import br.com.chfb.ativo.assetmanagement.entity.AssetType;
import br.com.chfb.ativo.assetmanagement.entity.VerificationStatus;
import br.com.chfb.ativo.assetmanagement.exception.AssetTypeConflictException;
import br.com.chfb.ativo.assetmanagement.exception.SymbolNotFoundException;
import br.com.chfb.ativo.assetmanagement.repository.AssetRepository;
import br.com.chfb.ativo.sharedkernel.market.SymbolCheck;
import br.com.chfb.ativo.sharedkernel.market.SymbolVerifier;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Como um símbolo entra no catálogo compartilhado, seja por uma compra ou por um acompanhamento. */
@Service
public class AssetRegistrar {

    private final AssetRepository assetRepository;
    private final SymbolVerifier symbolVerifier;

    public AssetRegistrar(AssetRepository assetRepository, SymbolVerifier symbolVerifier) {
        this.assetRepository = assetRepository;
        this.symbolVerifier = symbolVerifier;
    }

    static String normalize(String symbol) {
        return symbol.toUpperCase(Locale.ROOT);
    }

    /**
     * Só o primeiro cadastro de um símbolo é conferido. Sem resposta do tipo escolhido, o outro tipo ainda
     * pode revelar um engano, como BTC lançado como ação quando a Brapi está sem token. Chame fora de
     * transação: uma API lenta não pode segurar travas.
     */
    VerificationStatus verifyNew(String rawSymbol, AssetType assetType, boolean acceptUnverified) {
        String symbol = normalize(rawSymbol);
        if (acceptUnverified || assetRepository.findBySymbol(symbol).isPresent()) {
            return VerificationStatus.UNVERIFIED;
        }
        SymbolCheck chosen = symbolVerifier.verify(symbol, assetType.toAssetClass());
        if (chosen == SymbolCheck.FOUND) {
            return VerificationStatus.VERIFIED;
        }
        AssetType otherType = assetType == AssetType.CRYPTO ? AssetType.STOCK : AssetType.CRYPTO;
        if (symbolVerifier.verify(symbol, otherType.toAssetClass()) == SymbolCheck.FOUND) {
            throw new SymbolNotFoundException(symbol, otherType);
        }
        if (chosen == SymbolCheck.NOT_FOUND) {
            throw new SymbolNotFoundException(symbol, null);
        }
        return VerificationStatus.UNVERIFIED;
    }

    Asset findOrRegister(String rawSymbol, String rawName, AssetType assetType, VerificationStatus status) {
        String symbol = normalize(rawSymbol);
        Asset asset = assetRepository.findBySymbol(symbol).orElseGet(() -> {
            String name = rawName == null || rawName.isBlank() ? symbol : rawName.strip();
            assetRepository.insertIfAbsent(UUID.randomUUID(), symbol, name, assetType.name(), status.name());
            return assetRepository.findBySymbol(symbol).orElseThrow();
        });
        if (asset.getAssetType() != assetType) {
            throw new AssetTypeConflictException(symbol, asset.getAssetType());
        }
        return asset;
    }
}
