package com.suptech.postventa.domain.exception;

public abstract class DominioException extends RuntimeException {

    private final String codigo;

    protected DominioException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
