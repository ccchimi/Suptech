package com.suptech.postventa.application.service;

import com.suptech.postventa.domain.model.Caso;
import com.suptech.postventa.domain.model.saga.SagaCancelacion;
import com.suptech.postventa.domain.port.out.CasoRepositoryPort;
import com.suptech.postventa.domain.port.out.SagaRepositoryPort;
import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class SagaReconciliadorService {

    private final SagaRepositoryPort sagaRepository;
    private final CasoRepositoryPort casoRepository;
    private final CancelacionSagaService orquestador;
    private final AsyncTaskExecutor applicationTaskExecutor;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    @Value("${postventa.saga.reconciliacion.tamano-lote:100}")
    private int tamanoLote;

    @Scheduled(
            fixedDelayString = "${postventa.saga.reconciliacion.intervalo:PT15S}",
            initialDelayString = "${postventa.saga.reconciliacion.retardo-inicial:PT30S}")
    @SchedulerLock(name = "reintentarSagasPendientes", lockAtMostFor = "PT5M", lockAtLeastFor = "PT5S")
    public void reintentarSagasPendientes() {
        List<SagaCancelacion> vencidas =
                sagaRepository.buscarReintentosVencidos(clock.instant(), tamanoLote);

        if (vencidas.isEmpty()) {
            return;
        }
        log.info("Reconciliador: {} saga(s) de cancelacion pendientes de reintento", vencidas.size());
        meterRegistry.counter("postventa.saga.reintentos").increment(vencidas.size());

        List<CompletableFuture<Void>> tareas = vencidas.stream()
                .map(saga -> CompletableFuture.runAsync(() -> reintentar(saga), applicationTaskExecutor))
                .toList();

        CompletableFuture.allOf(tareas.toArray(CompletableFuture[]::new)).join();
    }

    private void reintentar(SagaCancelacion saga) {
        try {
            Caso caso = casoRepository.buscarPorId(saga.casoId()).orElse(null);
            if (caso == null) {
                log.error("Saga {} huerfana: no existe el caso {}", saga.id(), saga.casoId());
                return;
            }
            SagaCancelacion avanzada = orquestador.avanzar(saga, caso);
            if (avanzada.estado().esTerminal()) {
                meterRegistry.counter("postventa.saga.finalizadas",
                        "estado", avanzada.estado().name()).increment();
            }
        } catch (RuntimeException e) {
            log.error("Error no controlado reintentando la saga {}", saga.id(), e);
        }
    }
}
