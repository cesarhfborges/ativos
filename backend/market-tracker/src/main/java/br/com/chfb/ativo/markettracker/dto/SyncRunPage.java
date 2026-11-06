package br.com.chfb.ativo.markettracker.dto;

import java.util.List;

public record SyncRunPage(List<SyncRunResponse> items, long total) {
}
