package com.suptech.postventa.domain.exception;

public class ServicioExternoNoDisponibleException extends DominioException {

    public ServicioExternoNoDisponibleException(String servicio, String detalle) {
        super("SERVICIO_EXTERNO_NO_DISPONIBLE",
                "El servicio %s no esta disponible: %s".formatted(servicio, detalle));
    }
}
