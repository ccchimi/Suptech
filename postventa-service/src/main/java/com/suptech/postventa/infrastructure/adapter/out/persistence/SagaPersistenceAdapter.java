package com.suptech.postventa.infrastructure.adapter.out.persistence;

import com.suptech.postventa.domain.model.saga.EstadoSaga;
import com.suptech.postventa.domain.model.saga.SagaCancelacion;
import com.suptech.postventa.domain.port.out.SagaRepositoryPort;
import com.suptech.postventa.infrastructure.adapter.out.persistence.entity.SagaCancelacionJpaEntity;
import com.suptech.postventa.infrastructure.adapter.out.persistence.repository.SpringDataSagaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SagaPersistenceAdapter implements SagaRepositoryPort {

    private static final Set<EstadoSaga> ESTADOS_ACTIVOS = Set.of(
            EstadoSaga.INICIADA,
            EstadoSaga.PEDIDO_CANCELADO,
            EstadoSaga.PENDIENTE_REINTENTO,
            EstadoSaga.REQUIERE_INTERVENCION);

    private final SpringDataSagaRepository repositorio;

    @Override
    public SagaCancelacion guardar(SagaCancelacion saga) {
        SagaCancelacionJpaEntity entidad =
                repositorio.findById(saga.id()).orElseGet(SagaCancelacionJpaEntity::new);
        SagaPersistenceMapper.volcar(saga, entidad);
        return SagaPersistenceMapper.aDominio(repositorio.save(entidad));
    }

    @Override
    public Optional<SagaCancelacion> buscarPorId(UUID sagaId) {
        return repositorio.findById(sagaId).map(SagaPersistenceMapper::aDominio);
    }

    @Override
    public Optional<SagaCancelacion> buscarActivaPorPedido(String pedidoId) {
        return repositorio
                .findFirstByPedidoIdAndEstadoInOrderByCreadoEnDesc(pedidoId, ESTADOS_ACTIVOS)
                .map(SagaPersistenceMapper::aDominio);
    }

    @Override
    public List<SagaCancelacion> buscarReintentosVencidos(Instant limite, int maxResultados) {
        return repositorio
                .findReintentosVencidos(EstadoSaga.PENDIENTE_REINTENTO, limite, Limit.of(maxResultados))
                .stream()
                .map(SagaPersistenceMapper::aDominio)
                .toList();
    }
}
