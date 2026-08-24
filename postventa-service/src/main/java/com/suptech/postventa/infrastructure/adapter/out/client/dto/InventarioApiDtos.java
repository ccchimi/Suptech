package com.suptech.postventa.infrastructure.adapter.out.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

public final class InventarioApiDtos {

    private InventarioApiDtos() {
    }

    public record LiberarStockRequest(String pedidoId, String motivo, List<ItemRequest> items) { }

    public record ItemRequest(String sku, int cantidad) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LiberarStockResponse(String referencia, String estado) { }
}
