package com.suptech.postventa.domain.port.in.command;

import com.suptech.postventa.domain.model.TipoCaso;

import java.math.BigDecimal;
import java.util.List;

public record RegistrarCasoCommand(
        String pedidoId,
        String clienteId,
        TipoCaso tipo,
        String motivo,
        BigDecimal montoSolicitado,
        List<LineaCommand> lineas
) {
    public RegistrarCasoCommand {
        lineas = lineas == null ? List.of() : List.copyOf(lineas);
    }

    public record LineaCommand(String sku, int cantidad, String detalle) { }
}
