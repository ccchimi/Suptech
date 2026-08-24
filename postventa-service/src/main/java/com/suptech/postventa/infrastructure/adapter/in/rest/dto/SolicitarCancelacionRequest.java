package com.suptech.postventa.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SolicitarCancelacionRequest(

        @NotBlank(message = "pedidoId es obligatorio")
        String pedidoId,

        @NotBlank(message = "clienteId es obligatorio")
        String clienteId,

        @NotBlank(message = "motivo es obligatorio")
        @Size(max = 500)
        String motivo
) { }
