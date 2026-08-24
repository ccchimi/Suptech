package com.suptech.postventa.domain.exception;

import java.util.UUID;

public class CasoNoEncontradoException extends DominioException {

    public CasoNoEncontradoException(UUID casoId) {
        super("CASO_NO_ENCONTRADO", "No existe el caso de postventa " + casoId);
    }
}
