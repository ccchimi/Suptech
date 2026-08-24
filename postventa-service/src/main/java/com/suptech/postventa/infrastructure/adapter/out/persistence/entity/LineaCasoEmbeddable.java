package com.suptech.postventa.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
public class LineaCasoEmbeddable {

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(nullable = false)
    private int cantidad;

    @Column(name = "motivo_detalle", length = 500)
    private String motivoDetalle;
}
