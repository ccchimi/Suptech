package com.suptech.postventa.infrastructure.adapter.out.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

public final class PedidosApiDtos {

    private PedidosApiDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PedidoResponse(
            String pedidoId,
            String clienteId,
            String estado,
            List<LineaPedidoResponse> lineas
    ) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LineaPedidoResponse(String sku, int cantidad) { }

    public record CancelarPedidoRequest(String motivo, String solicitadoPor) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CancelarPedidoResponse(String pedidoId, String estado, String referencia) { }
}
