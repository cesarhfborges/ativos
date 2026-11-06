package br.com.chfb.ativo.assetmanagement.entity;

/** Situação de uma operação no extrato: {@code ACTIVE} conta nos cálculos; {@code REVERSED} foi estornada. */
public enum TransactionStatus {
    ACTIVE,
    REVERSED
}
