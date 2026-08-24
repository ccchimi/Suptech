package com.suptech.postventa.application.service;

import com.suptech.postventa.domain.model.Caso;
import com.suptech.postventa.domain.model.EstadoCaso;
import com.suptech.postventa.domain.model.saga.SagaCancelacion;
import com.suptech.postventa.domain.port.out.CasoRepositoryPort;
import com.suptech.postventa.domain.port.out.SagaRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Slf4j
@Component
@RequiredArgsConstructor
public class SagaEstadoWriter {

    private final CasoRepositoryPort casoRepository;
    private final SagaRepositoryPort sagaRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void crear(Caso caso, SagaCancelacion saga) {
        casoRepository.guardar(caso);
        sagaRepository.guardar(saga);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistirAvance(SagaCancelacion saga, Caso caso) {
        sagaRepository.guardar(saga);
        sincronizarCaso(saga, caso);
        casoRepository.guardar(caso);
    }

    private void sincronizarCaso(SagaCancelacion saga, Caso caso) {
        if (caso.estado().esTerminal()) {
            return;
        }
        switch (saga.estado()) {
            case COMPLETADA -> caso.resolver(
                    "Pedido cancelado y stock liberado", clock.instant());
            case FALLIDA -> caso.rechazar(
                    "No fue posible cancelar el pedido: " + saga.ultimoError(), clock.instant());
            case REQUIERE_INTERVENCION -> {
                if (caso.estado() != EstadoCaso.REQUIERE_INTERVENCION) {
                    caso.escalar("Cancelacion incompleta, requiere backoffice: " + saga.ultimoError(),
                            clock.instant());
                }
                log.error("Saga {} del pedido {} escalada a backoffice tras {} intentos. Ultimo error: {}",
                        saga.id(), saga.pedidoId(), saga.intentos(), saga.ultimoError());
            }
            case INICIADA, PEDIDO_CANCELADO, PENDIENTE_REINTENTO -> {
            }
        }
    }
}
