package com.suptech.postventa.domain.port.in;

import com.suptech.postventa.domain.model.EstadoCaso;
import com.suptech.postventa.domain.model.saga.EstadoSaga;
import com.suptech.postventa.domain.port.in.command.SolicitarCancelacionCommand;

import java.util.UUID;

public interface SolicitarCancelacionUseCase {

    ResultadoCancelacion solicitar(SolicitarCancelacionCommand comando);

    record ResultadoCancelacion(
            UUID casoId,
            UUID sagaId,
            EstadoCaso estadoCaso,
            EstadoSaga estadoSaga,
            boolean requiereSeguimiento,
            String detalle
    ) { }
}
