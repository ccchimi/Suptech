package com.suptech.postventa.domain.port.in;

import com.suptech.postventa.domain.model.Caso;

import java.util.List;
import java.util.UUID;

public interface ConsultarCasosQuery {

    Caso porId(UUID casoId);

    List<Caso> porCliente(String clienteId);
}
