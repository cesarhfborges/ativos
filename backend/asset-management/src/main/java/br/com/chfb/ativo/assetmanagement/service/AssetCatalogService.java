package br.com.chfb.ativo.assetmanagement.service;

import br.com.chfb.ativo.assetmanagement.dto.AssetCatalogEntry;
import br.com.chfb.ativo.assetmanagement.dto.AssetCatalogPage;
import br.com.chfb.ativo.assetmanagement.dto.AssetVerification;
import br.com.chfb.ativo.assetmanagement.entity.Asset;
import br.com.chfb.ativo.assetmanagement.entity.AssetType;
import br.com.chfb.ativo.assetmanagement.entity.VerificationStatus;
import br.com.chfb.ativo.assetmanagement.exception.AssetNotFoundException;
import br.com.chfb.ativo.assetmanagement.exception.BusinessRuleException;
import br.com.chfb.ativo.assetmanagement.repository.AssetRepository;
import br.com.chfb.ativo.assetmanagement.repository.WalletPositionRepository;
import br.com.chfb.ativo.assetmanagement.repository.WalletTransactionRepository;
import br.com.chfb.ativo.sharedkernel.market.SymbolCheck;
import br.com.chfb.ativo.sharedkernel.market.SymbolVerifier;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manutenção do catálogo compartilhado. Aqui a consulta ao provedor pode rodar dentro da transação:
 * são ações raras de um admin e não seguram a trava de nenhuma carteira.
 */
@Service
public class AssetCatalogService {

    private final AssetRepository assetRepository;
    private final WalletPositionRepository positionRepository;
    private final WalletTransactionRepository transactionRepository;
    private final SymbolVerifier symbolVerifier;

    public AssetCatalogService(
            AssetRepository assetRepository,
            WalletPositionRepository positionRepository,
            WalletTransactionRepository transactionRepository,
            SymbolVerifier symbolVerifier) {
        this.assetRepository = assetRepository;
        this.positionRepository = positionRepository;
        this.transactionRepository = transactionRepository;
        this.symbolVerifier = symbolVerifier;
    }

    @Transactional(readOnly = true)
    public AssetCatalogPage list(String query, int page, int size) {
        String term = query == null ? "" : query.strip().toUpperCase(Locale.ROOT).replaceAll("([!%_])", "!$1");
        Page<Asset> assets = assetRepository.searchPage(
                term + "%", "%" + term + "%", PageRequest.of(page, size, Sort.by("symbol")));
        List<UUID> ids = assets.stream().map(Asset::getId).toList();
        Map<UUID, Long> open = counts(ids.isEmpty() ? List.of() : positionRepository.countOpenByAsset(ids));
        Map<UUID, Long> operations = counts(ids.isEmpty() ? List.of() : transactionRepository.countByAsset(ids));
        return new AssetCatalogPage(
                assets.stream().map(asset -> entry(asset, open, operations)).toList(), assets.getTotalElements());
    }

    /** Mudar o tipo invalida a conferência anterior, que valia para o outro provedor. */
    @Transactional
    public AssetCatalogEntry update(UUID id, String name, AssetType assetType) {
        Asset asset = find(id);
        boolean typeChanged = asset.getAssetType() != assetType;
        asset.update(name.strip(), assetType);
        if (typeChanged) {
            SymbolCheck check = symbolVerifier.verify(asset.getSymbol(), assetType.toAssetClass());
            asset.markVerification(check == SymbolCheck.FOUND ? VerificationStatus.VERIFIED : VerificationStatus.UNVERIFIED);
        }
        return entry(asset);
    }

    /** Se o provedor não responder, a situação do ativo fica como está. */
    @Transactional
    public AssetVerification verify(UUID id) {
        Asset asset = find(id);
        SymbolCheck check = symbolVerifier.verify(asset.getSymbol(), asset.getAssetType().toAssetClass());
        if (check != SymbolCheck.UNAVAILABLE) {
            asset.markVerification(check == SymbolCheck.FOUND ? VerificationStatus.VERIFIED : VerificationStatus.UNVERIFIED);
        }
        return new AssetVerification(entry(asset), check);
    }

    /** Devolve o símbolo removido. Um ativo com operações faz parte do histórico de alguém e não pode sair. */
    @Transactional
    public String remove(UUID id) {
        Asset asset = find(id);
        if (!counts(transactionRepository.countByAsset(List.of(id))).isEmpty()) {
            throw new BusinessRuleException("Este ativo tem operações registradas e não pode ser removido");
        }
        assetRepository.delete(asset);
        return asset.getSymbol();
    }

    private Asset find(UUID id) {
        return assetRepository.findById(id).orElseThrow(AssetNotFoundException::new);
    }

    private AssetCatalogEntry entry(Asset asset) {
        List<UUID> ids = List.of(asset.getId());
        return entry(asset, counts(positionRepository.countOpenByAsset(ids)), counts(transactionRepository.countByAsset(ids)));
    }

    private static AssetCatalogEntry entry(Asset asset, Map<UUID, Long> open, Map<UUID, Long> operations) {
        return AssetCatalogEntry.from(asset, open.getOrDefault(asset.getId(), 0L), operations.getOrDefault(asset.getId(), 0L));
    }

    private static Map<UUID, Long> counts(Collection<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));
    }
}
