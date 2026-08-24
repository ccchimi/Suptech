package com.suptech.postventa.infrastructure.adapter.out.client;

public class IntegracionTransitoriaException extends RuntimeException {

    public IntegracionTransitoriaException(String mensaje) {
        super(mensaje);
    }

    public IntegracionTransitoriaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
