package com.suptech.postventa.domain.port.out;

import com.suptech.postventa.domain.model.Caso;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CasoRepositoryPort {

    Caso guardar(Caso caso);

    Optional<Caso> buscarPorId(UUID casoId);

    List<Caso> buscarPorCliente(String clienteId);
}
