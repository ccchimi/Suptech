package com.suptech.postventa.domain.port.in.command;

public record SolicitarCancelacionCommand(
        String pedidoId,
        String clienteId,
        String motivo,
        String claveIdempotencia
) { }
