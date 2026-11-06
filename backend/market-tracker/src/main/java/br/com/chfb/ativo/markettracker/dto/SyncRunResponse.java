package br.com.chfb.ativo.markettracker.dto;

import br.com.chfb.ativo.markettracker.entity.SyncOrigin;
import br.com.chfb.ativo.markettracker.entity.SyncRunStatus;
import java.time.Instant;
import java.util.UUID;

public record SyncRunResponse(
        UUID id,
        SyncOrigin origin,
        SyncRunStatus status,
        Instant startedAt,
        Instant finishedAt,
        int requestedSymbols,
        int updatedSymbols,
        String message) {
}
