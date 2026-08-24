package com.suptech.postventa.infrastructure.adapter.out.persistence.entity;

import com.suptech.postventa.domain.model.saga.EstadoSaga;
import com.suptech.postventa.domain.model.saga.PasoSaga;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "saga_cancelacion", indexes = {
        @Index(name = "idx_saga_pedido", columnList = "pedido_id"),
        @Index(name = "idx_saga_reintentos", columnList = "estado, proximo_intento_en")
})
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class SagaCancelacionJpaEntity {

    @Id
    private UUID id;

    @Column(name = "caso_id", nullable = false, unique = true)
    private UUID casoId;

    @Column(name = "pedido_id", nullable = false)
    private String pedidoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoSaga estado;

    @Enumerated(EnumType.STRING)
    @Column(name = "paso_pendiente", nullable = false, length = 30)
    private PasoSaga pasoPendiente;

    @Column(nullable = false)
    private int intentos;

    @Column(name = "proximo_intento_en")
    private Instant proximoIntentoEn;

    @Column(name = "ultimo_error", length = 1000)
    private String ultimoError;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @Version
    private long version;
}
