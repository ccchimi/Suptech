package com.suptech.postventa.domain.port.out;

import com.suptech.postventa.domain.model.LineaAfectada;
import com.suptech.postventa.domain.model.ResultadoIntegracion;

import java.util.List;
import java.util.Optional;

public interface PedidosPort {

    Optional<PedidoSnapshot> consultarPedido(String pedidoId);

    ResultadoIntegracion cancelarPedido(ComandoCancelarPedido comando);

    ResultadoIntegracion revertirCancelacion(String pedidoId, String claveIdempotencia);

    record PedidoSnapshot(
            String pedidoId,
            String clienteId,
            String estado,
            boolean cancelable,
            String motivoNoCancelable,
            List<LineaAfectada> lineas
    ) { }

    record ComandoCancelarPedido(String pedidoId, String motivo, String claveIdempotencia) { }
}
