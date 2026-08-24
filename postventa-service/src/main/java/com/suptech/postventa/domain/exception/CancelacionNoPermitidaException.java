package com.suptech.postventa.domain.exception;

public class CancelacionNoPermitidaException extends DominioException {

    public CancelacionNoPermitidaException(String pedidoId, String motivo) {
        super("CANCELACION_NO_PERMITIDA",
                "El pedido %s no puede cancelarse: %s".formatted(pedidoId, motivo));
    }
}
