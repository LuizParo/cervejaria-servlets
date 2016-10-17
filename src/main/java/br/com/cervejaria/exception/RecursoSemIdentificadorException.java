package br.com.cervejaria.exception;

public class RecursoSemIdentificadorException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public RecursoSemIdentificadorException(String message, Throwable cause) {
        super(message, cause);
    }

    public RecursoSemIdentificadorException(String message) {
        super(message);
    }

    public RecursoSemIdentificadorException(Throwable cause) {
        super(cause);
    }
}