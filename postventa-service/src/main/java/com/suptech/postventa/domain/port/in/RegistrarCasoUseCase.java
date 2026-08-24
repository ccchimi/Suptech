package com.suptech.postventa.domain.port.in;

import com.suptech.postventa.domain.model.Caso;
import com.suptech.postventa.domain.port.in.command.RegistrarCasoCommand;

public interface RegistrarCasoUseCase {

    Caso registrar(RegistrarCasoCommand comando);
}
