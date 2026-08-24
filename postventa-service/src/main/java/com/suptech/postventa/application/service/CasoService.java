package com.suptech.postventa.application.service;

import com.suptech.postventa.domain.exception.CasoNoEncontradoException;
import com.suptech.postventa.domain.model.Caso;
import com.suptech.postventa.domain.model.LineaAfectada;
import com.suptech.postventa.domain.port.in.ConsultarCasosQuery;
import com.suptech.postventa.domain.port.in.RegistrarCasoUseCase;
import com.suptech.postventa.domain.port.in.command.RegistrarCasoCommand;
import com.suptech.postventa.domain.port.out.CasoRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CasoService implements RegistrarCasoUseCase, ConsultarCasosQuery {

    private final CasoRepositoryPort casoRepository;
    private final Clock clock;

    @Override
    @Transactional
    public Caso registrar(RegistrarCasoCommand comando) {
        List<LineaAfectada> lineas = comando.lineas().stream()
                .map(linea -> new LineaAfectada(linea.sku(), linea.cantidad(), linea.detalle()))
                .toList();

        Caso caso = Caso.abrir(
                comando.pedidoId(),
                comando.clienteId(),
                comando.tipo(),
                comando.motivo(),
                comando.montoSolicitado(),
                lineas,
                clock.instant());

        Caso guardado = casoRepository.guardar(caso);
        log.info("Caso {} de tipo {} abierto para el pedido {}", guardado.id(), guardado.tipo(), guardado.pedidoId());
        return guardado;
    }

    @Override
    @Transactional(readOnly = true)
    public Caso porId(UUID casoId) {
        return casoRepository.buscarPorId(casoId)
                .orElseThrow(() -> new CasoNoEncontradoException(casoId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Caso> porCliente(String clienteId) {
        return casoRepository.buscarPorCliente(clienteId);
    }
}
