package com.suptech.postventa.infrastructure.adapter.in.rest.dto;

import com.suptech.postventa.domain.model.Caso;
import com.suptech.postventa.domain.model.EstadoCaso;
import com.suptech.postventa.domain.model.TipoCaso;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CasoResponse(
        UUID id,
        String pedidoId,
        String clienteId,
        TipoCaso tipo,
        EstadoCaso estado,
        String motivo,
        BigDecimal montoSolicitado,
        String resolucion,
        List<LineaResponse> lineas,
        Instant creadoEn,
        Instant actualizadoEn
) {

    public record LineaResponse(String sku, int cantidad, String detalle) { }

    public static CasoResponse desde(Caso caso) {
        List<LineaResponse> lineas = caso.lineas().stream()
                .map(linea -> new LineaResponse(linea.sku(), linea.cantidad(), linea.motivoDetalle()))
                .toList();

        return new CasoResponse(
                caso.id(),
                caso.pedidoId(),
                caso.clienteId(),
                caso.tipo(),
                caso.estado(),
                caso.motivo(),
                caso.montoSolicitado(),
                caso.resolucion(),
                lineas,
                caso.creadoEn(),
                caso.actualizadoEn());
    }
}
