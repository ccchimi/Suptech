package com.suptech.postventa.domain.model;

import java.util.Objects;

public record LineaAfectada(String sku, int cantidad, String motivoDetalle) {

    public LineaAfectada {
        Objects.requireNonNull(sku, "sku es obligatorio");
        if (sku.isBlank()) {
            throw new IllegalArgumentException("sku no puede estar vacio");
        }
        if (cantidad <= 0) {
            throw new IllegalArgumentException("cantidad debe ser mayor a cero para el sku " + sku);
        }
    }

    public static LineaAfectada de(String sku, int cantidad) {
        return new LineaAfectada(sku, cantidad, null);
    }
}
