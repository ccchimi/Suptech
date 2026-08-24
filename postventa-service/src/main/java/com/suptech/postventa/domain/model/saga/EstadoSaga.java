package com.suptech.postventa.domain.model.saga;

public enum EstadoSaga {

    INICIADA,
    PEDIDO_CANCELADO,
    PENDIENTE_REINTENTO,
    COMPLETADA,
    FALLIDA,
    REQUIERE_INTERVENCION;

    public boolean esTerminal() {
        return this == COMPLETADA || this == FALLIDA || this == REQUIERE_INTERVENCION;
    }
}
