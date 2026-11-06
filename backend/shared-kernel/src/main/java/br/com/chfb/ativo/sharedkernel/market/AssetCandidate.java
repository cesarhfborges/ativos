package br.com.chfb.ativo.sharedkernel.market;

import br.com.chfb.ativo.sharedkernel.portfolio.AssetClass;
import java.math.BigDecimal;

/** Ativo encontrado no provedor de cotações. {@code price} é nulo quando o provedor não informa a cotação. */
public record AssetCandidate(String symbol, String name, AssetClass assetType, BigDecimal price) {
}
