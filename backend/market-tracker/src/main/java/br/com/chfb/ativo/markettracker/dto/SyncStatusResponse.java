package br.com.chfb.ativo.markettracker.dto;

import java.time.Instant;

/**
 * Visível a todos os usuários. {@code lastSyncAt} é a última coleta geral que atualizou preços;
 * {@code lastRunFailed} avisa que a execução mais recente teve falhas. {@code nextSyncAt} é nulo com a coleta pausada.
 */
public record SyncStatusResponse(Instant lastSyncAt, Instant nextSyncAt, boolean lastRunFailed, boolean collectionEnabled) {
}
