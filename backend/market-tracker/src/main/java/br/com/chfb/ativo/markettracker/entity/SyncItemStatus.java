package br.com.chfb.ativo.markettracker.entity;

/** {@code NO_DATA}: não houve erro, mas também não há o que gravar, como um grupo sem provedor. */
public enum SyncItemStatus {
    UPDATED, NO_DATA, FAILED
}
