package br.com.chfb.ativo.assetmanagement.dto;

import java.math.BigDecimal;
import java.util.List;

public record WalletSummaryResponse(BigDecimal fiatBalance, List<PositionResponse> positions) {
}
