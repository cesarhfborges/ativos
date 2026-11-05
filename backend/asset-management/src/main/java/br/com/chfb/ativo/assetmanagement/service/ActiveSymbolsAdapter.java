package br.com.chfb.ativo.assetmanagement.service;

import br.com.chfb.ativo.assetmanagement.entity.Asset;
import br.com.chfb.ativo.assetmanagement.entity.AssetType;
import br.com.chfb.ativo.assetmanagement.repository.WalletPositionRepository;
import br.com.chfb.ativo.sharedkernel.market.ActiveSymbols;
import br.com.chfb.ativo.sharedkernel.market.ActiveSymbolsProvider;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActiveSymbolsAdapter implements ActiveSymbolsProvider {

    private final WalletPositionRepository positionRepository;

    public ActiveSymbolsAdapter(WalletPositionRepository positionRepository) {
        this.positionRepository = positionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public ActiveSymbols activeSymbols() {
        List<Asset> assets = positionRepository.findActiveAssets();
        return new ActiveSymbols(symbolsOf(assets, AssetType.CRYPTO), symbolsOf(assets, AssetType.STOCK));
    }

    private static Set<String> symbolsOf(List<Asset> assets, AssetType type) {
        return assets.stream()
                .filter(asset -> asset.getAssetType() == type)
                .map(Asset::getSymbol)
                .collect(Collectors.toSet());
    }
}
