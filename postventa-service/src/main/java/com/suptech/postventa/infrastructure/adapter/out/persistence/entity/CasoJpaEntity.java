package com.suptech.postventa.infrastructure.adapter.out.persistence.entity;

import com.suptech.postventa.domain.model.EstadoCaso;
import com.suptech.postventa.domain.model.TipoCaso;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "caso_postventa")
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class CasoJpaEntity {

    @Id
    private UUID id;

    @Column(name = "pedido_id", nullable = false)
    private String pedidoId;

    @Column(name = "cliente_id", nullable = false)
    private String clienteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoCaso tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoCaso estado;

    @Column(nullable = false, length = 500)
    private String motivo;

    @Column(name = "monto_solicitado", precision = 19, scale = 4)
    private BigDecimal montoSolicitado;

    @Column(length = 1000)
    private String resolucion;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "caso_linea", joinColumns = @JoinColumn(name = "caso_id"))
    private List<LineaCasoEmbeddable> lineas = new ArrayList<>();

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @Version
    private long version;
}
