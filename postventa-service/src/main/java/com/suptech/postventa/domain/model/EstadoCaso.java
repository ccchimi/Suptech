package com.suptech.postventa.domain.model;

import java.util.Set;

public enum EstadoCaso {

    RECIBIDO,
    EN_PROCESO,
    RESUELTO,
    RECHAZADO,
    REQUIERE_INTERVENCION;

    public boolean puedeTransicionarA(EstadoCaso destino) {
        return switch (this) {
            case RECIBIDO -> Set.of(EN_PROCESO, RECHAZADO).contains(destino);
            case EN_PROCESO -> Set.of(RESUELTO, RECHAZADO, REQUIERE_INTERVENCION).contains(destino);
            case REQUIERE_INTERVENCION -> Set.of(RESUELTO, RECHAZADO).contains(destino);
            case RESUELTO, RECHAZADO -> false;
        };
    }

    public boolean esTerminal() {
        return this == RESUELTO || this == RECHAZADO;
    }
}
