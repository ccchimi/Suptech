package com.suptech.postventa.domain.port.out;

import com.suptech.postventa.domain.model.saga.SagaCancelacion;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SagaRepositoryPort {

    SagaCancelacion guardar(SagaCancelacion saga);

    Optional<SagaCancelacion> buscarPorId(UUID sagaId);

    Optional<SagaCancelacion> buscarActivaPorPedido(String pedidoId);

    List<SagaCancelacion> buscarReintentosVencidos(Instant limite, int maxResultados);
}
