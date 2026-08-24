package com.suptech.postventa.infrastructure.adapter.out.persistence.repository;

import com.suptech.postventa.domain.model.saga.EstadoSaga;
import com.suptech.postventa.infrastructure.adapter.out.persistence.entity.SagaCancelacionJpaEntity;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataSagaRepository extends JpaRepository<SagaCancelacionJpaEntity, UUID> {

    Optional<SagaCancelacionJpaEntity> findByCasoId(UUID casoId);

    Optional<SagaCancelacionJpaEntity> findFirstByPedidoIdAndEstadoInOrderByCreadoEnDesc(
            String pedidoId, Collection<EstadoSaga> estados);

    @Query("""
            select s from SagaCancelacionJpaEntity s
            where s.estado = :estado
              and s.proximoIntentoEn is not null
              and s.proximoIntentoEn <= :limite
            order by s.proximoIntentoEn asc
            """)
    List<SagaCancelacionJpaEntity> findReintentosVencidos(@Param("estado") EstadoSaga estado,
                                                          @Param("limite") Instant limite,
                                                          Limit maxResultados);
}
