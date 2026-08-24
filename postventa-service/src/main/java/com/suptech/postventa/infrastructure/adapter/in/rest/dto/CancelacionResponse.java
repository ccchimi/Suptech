package com.suptech.postventa.infrastructure.adapter.in.rest.dto;

import com.suptech.postventa.domain.model.EstadoCaso;
import com.suptech.postventa.domain.model.saga.EstadoSaga;
import com.suptech.postventa.domain.port.in.SolicitarCancelacionUseCase.ResultadoCancelacion;

import java.util.UUID;

public record CancelacionResponse(
        UUID casoId,
        UUID sagaId,
        EstadoCaso estadoCaso,
        EstadoSaga estadoSaga,
        String detalle,
        String seguimiento
) {

    public static CancelacionResponse desde(ResultadoCancelacion resultado) {
        return new CancelacionResponse(
                resultado.casoId(),
                resultado.sagaId(),
                resultado.estadoCaso(),
                resultado.estadoSaga(),
                resultado.detalle(),
                "/api/v1/casos/" + resultado.casoId());
    }
}
