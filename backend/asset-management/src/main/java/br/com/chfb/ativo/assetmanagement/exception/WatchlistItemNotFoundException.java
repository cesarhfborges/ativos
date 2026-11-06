package br.com.chfb.ativo.assetmanagement.exception;

/** Também vale para o item de outro usuário: a resposta não revela que ele existe. */
public class WatchlistItemNotFoundException extends RuntimeException {

    public WatchlistItemNotFoundException() {
        super("Ativo não encontrado no seu acompanhamento");
    }
}
