package com.suptech.postventa.domain.exception;

import com.suptech.postventa.domain.model.EstadoCaso;

import java.util.UUID;

public class TransicionInvalidaException extends DominioException {

    public TransicionInvalidaException(UUID casoId, EstadoCaso origen, EstadoCaso destino) {
        super("TRANSICION_INVALIDA",
                "El caso %s no puede pasar de %s a %s".formatted(casoId, origen, destino));
    }
}
