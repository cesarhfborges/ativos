package br.com.chfb.ativo.markettracker.service;

import br.com.chfb.ativo.markettracker.dto.SyncItemResponse;
import br.com.chfb.ativo.markettracker.entity.SyncItemKind;
import br.com.chfb.ativo.markettracker.entity.SyncItemStatus;
import br.com.chfb.ativo.markettracker.entity.SyncOrigin;
import br.com.chfb.ativo.markettracker.entity.SyncRunStatus;
import br.com.chfb.ativo.sharedkernel.portfolio.AssetClass;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** O que aconteceu em uma coleta, acumulado em memória até ela terminar. */
public final class SyncRunRecord {

    private final UUID id = UUID.randomUUID();
    private final SyncOrigin origin;
    private final Instant startedAt = Instant.now();
    private final List<SyncItemResponse> items = new ArrayList<>();

    SyncRunRecord(SyncOrigin origin) {
        this.origin = origin;
    }

    void quote(String symbol, AssetClass assetClass, SyncItemStatus status, String message) {
        items.add(new SyncItemResponse(symbol, assetClass, SyncItemKind.QUOTE, status, null, message));
    }

    void history(String symbol, AssetClass assetClass, SyncItemStatus status, Integer points, String message) {
        items.add(new SyncItemResponse(symbol, assetClass, SyncItemKind.HISTORY, status, points, message));
    }

    UUID id() {
        return id;
    }

    SyncOrigin origin() {
        return origin;
    }

    Instant startedAt() {
        return startedAt;
    }

    List<SyncItemResponse> items() {
        return items;
    }

    int requestedSymbols() {
        return (int) items.stream().filter(item -> item.kind() == SyncItemKind.QUOTE).count();
    }

    int updatedSymbols() {
        return (int) items.stream()
                .filter(item -> item.kind() == SyncItemKind.QUOTE && item.status() == SyncItemStatus.UPDATED)
                .count();
    }

    /** Sem nenhum preço novo é falha, mesmo sem erro: as cotações continuam velhas. */
    SyncRunStatus status() {
        if (requestedSymbols() > 0 && updatedSymbols() == 0) {
            return SyncRunStatus.FAILED;
        }
        return items.stream().anyMatch(item -> item.status() == SyncItemStatus.FAILED)
                ? SyncRunStatus.PARTIAL
                : SyncRunStatus.SUCCESS;
    }
}
