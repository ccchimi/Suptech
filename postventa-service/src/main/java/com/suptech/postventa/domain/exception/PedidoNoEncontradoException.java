package com.suptech.postventa.domain.exception;

public class PedidoNoEncontradoException extends DominioException {

    public PedidoNoEncontradoException(String pedidoId) {
        super("PEDIDO_NO_ENCONTRADO", "El microservicio de Pedidos no conoce el pedido " + pedidoId);
    }
}
