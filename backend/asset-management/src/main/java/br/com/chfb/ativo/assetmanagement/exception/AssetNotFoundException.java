package br.com.chfb.ativo.assetmanagement.exception;

public class AssetNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AssetNotFoundException() {
        super("Ativo não encontrado");
    }
}
