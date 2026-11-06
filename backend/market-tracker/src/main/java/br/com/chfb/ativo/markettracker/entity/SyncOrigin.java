package br.com.chfb.ativo.markettracker.entity;

/** Quem disparou a coleta. */
public enum SyncOrigin {
    SCHEDULED, STARTUP, ADMIN, NEW_SYMBOL
}
