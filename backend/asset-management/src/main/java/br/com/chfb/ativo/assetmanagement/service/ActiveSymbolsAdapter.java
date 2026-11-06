package br.com.chfb.ativo.assetmanagement.service;

import br.com.chfb.ativo.assetmanagement.entity.Asset;
import br.com.chfb.ativo.assetmanagement.entity.AssetType;
import br.com.chfb.ativo.assetmanagement.repository.WalletPositionRepository;
import br.com.chfb.ativo.assetmanagement.repository.WatchlistItemRepository;
import br.com.chfb.ativo.sharedkernel.market.ActiveSymbols;
import br.com.chfb.ativo.sharedkernel.market.ActiveSymbolsProvider;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActiveSymbolsAdapter implements ActiveSymbolsProvider {

    private final WalletPositionRepository positionRepository;
    private final WatchlistItemRepository watchlistRepository;

    public ActiveSymbolsAdapter(WalletPositionRepository positionRepository, WatchlistItemRepository watchlistRepository) {
        this.positionRepository = positionRepository;
        this.watchlistRepository = watchlistRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public ActiveSymbols activeSymbols() {
        // Um conjunto: quem está em uma carteira e também é acompanhado continua sendo consultado uma vez só.
        Set<Asset> assets = new HashSet<>(positionRepository.findActiveAssets());
        assets.addAll(watchlistRepository.findWatchedAssets());
        return new ActiveSymbols(symbolsOf(assets, AssetType.CRYPTO), symbolsOf(assets, AssetType.STOCK));
    }

    private static Set<String> symbolsOf(Set<Asset> assets, AssetType type) {
        return assets.stream()
                .filter(asset -> asset.getAssetType() == type)
                .map(Asset::getSymbol)
                .collect(Collectors.toSet());
    }
}
