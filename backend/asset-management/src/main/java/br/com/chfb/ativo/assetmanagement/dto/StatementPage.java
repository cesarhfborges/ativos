package br.com.chfb.ativo.assetmanagement.dto;

import java.util.List;

public record StatementPage(List<TransactionResponse> items, boolean hasMore) {
}
