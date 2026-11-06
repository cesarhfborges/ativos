package br.com.chfb.ativo.markettracker.entity;

/** {@code INTERRUPTED}: a aplicação parou durante a coleta. */
public enum SyncRunStatus {
    RUNNING, SUCCESS, PARTIAL, FAILED, INTERRUPTED
}
