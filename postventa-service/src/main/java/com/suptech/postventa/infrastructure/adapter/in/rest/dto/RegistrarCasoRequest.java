package com.suptech.postventa.infrastructure.adapter.in.rest.dto;

import com.suptech.postventa.domain.model.TipoCaso;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record RegistrarCasoRequest(

        @NotBlank(message = "pedidoId es obligatorio")
        String pedidoId,

        @NotBlank(message = "clienteId es obligatorio")
        String clienteId,

        @NotNull(message = "tipo debe ser REEMBOLSO, RECLAMO o DEVOLUCION")
        TipoCaso tipo,

        @NotBlank
        @Size(max = 500)
        String motivo,

        @PositiveOrZero(message = "el monto no puede ser negativo")
        BigDecimal montoSolicitado,

        @Valid
        List<LineaRequest> lineas
) {

    public record LineaRequest(
            @NotBlank String sku,
            @Positive(message = "la cantidad debe ser mayor que cero") int cantidad,
            @Size(max = 500) String detalle
    ) { }
}
