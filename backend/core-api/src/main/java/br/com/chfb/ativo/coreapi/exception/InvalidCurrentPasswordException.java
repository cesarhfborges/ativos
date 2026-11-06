package br.com.chfb.ativo.coreapi.exception;

public class InvalidCurrentPasswordException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidCurrentPasswordException() {
        super("A senha atual está incorreta");
    }
}
