package br.com.chfb.ativo.assetmanagement.exception;

import br.com.chfb.ativo.assetmanagement.entity.AssetType;

public class AssetTypeConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AssetTypeConflictException(String symbol, AssetType registeredType) {
        super("O ativo " + symbol + " já está cadastrado como " + registeredType);
    }
}
