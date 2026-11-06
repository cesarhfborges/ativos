package br.com.chfb.ativo.assetmanagement.exception;

public class AlreadyWatchedException extends RuntimeException {

    public AlreadyWatchedException(String symbol) {
        super(symbol + " já está no seu acompanhamento");
    }
}
