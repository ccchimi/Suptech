package com.suptech.postventa.application.service;

import com.suptech.postventa.domain.exception.CancelacionNoPermitidaException;
import com.suptech.postventa.domain.exception.PedidoNoEncontradoException;
import com.suptech.postventa.domain.model.Caso;
import com.suptech.postventa.domain.model.LineaAfectada;
import com.suptech.postventa.domain.model.ResultadoIntegracion;
import com.suptech.postventa.domain.model.TipoCaso;
import com.suptech.postventa.domain.model.saga.EstadoSaga;
import com.suptech.postventa.domain.model.saga.PasoSaga;
import com.suptech.postventa.domain.model.saga.SagaCancelacion;
import com.suptech.postventa.domain.port.in.SolicitarCancelacionUseCase;
import com.suptech.postventa.domain.port.in.command.SolicitarCancelacionCommand;
import com.suptech.postventa.domain.port.out.CasoRepositoryPort;
import com.suptech.postventa.domain.port.out.InventarioPort;
import com.suptech.postventa.domain.port.out.PedidosPort;
import com.suptech.postventa.domain.port.out.SagaRepositoryPort;
import io.micrometer.core.annotation.Counted;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CancelacionSagaService implements SolicitarCancelacionUseCase {

    private final PedidosPort pedidosPort;
    private final InventarioPort inventarioPort;
    private final CasoRepositoryPort casoRepository;
    private final SagaRepositoryPort sagaRepository;
    private final SagaEstadoWriter estadoWriter;
    private final Clock clock;

    @Override
    @Observed(name = "postventa.cancelacion", contextualName = "solicitar-cancelacion")
    @Counted(value = "postventa.cancelacion.solicitudes")
    public ResultadoCancelacion solicitar(SolicitarCancelacionCommand comando) {
        var sagaVigente = sagaRepository.buscarActivaPorPedido(comando.pedidoId());
        if (sagaVigente.isPresent()) {
            SagaCancelacion saga = sagaVigente.get();
            log.info("Solicitud duplicada para el pedido {}: se reutiliza la saga {}",
                    comando.pedidoId(), saga.id());
            return vista(saga, casoRepository.buscarPorId(saga.casoId()).orElseThrow());
        }

        PedidosPort.PedidoSnapshot pedido = pedidosPort.consultarPedido(comando.pedidoId())
                .orElseThrow(() -> new PedidoNoEncontradoException(comando.pedidoId()));

        if (!pedido.cancelable()) {
            throw new CancelacionNoPermitidaException(comando.pedidoId(), pedido.motivoNoCancelable());
        }

        Caso caso = Caso.abrir(
                pedido.pedidoId(),
                comando.clienteId(),
                TipoCaso.CANCELACION,
                comando.motivo(),
                null,
                pedido.lineas(),
                clock.instant());
        caso.marcarEnProceso(clock.instant());

        SagaCancelacion saga = SagaCancelacion.iniciar(caso.id(), pedido.pedidoId(), clock.instant());
        estadoWriter.crear(caso, saga);

        avanzar(saga, caso);
        return vista(saga, caso);
    }

    public SagaCancelacion avanzar(SagaCancelacion saga, Caso caso) {
        if (saga.estado().esTerminal()) {
            return saga;
        }
        if (saga.pasoPendiente() == PasoSaga.CANCELAR_PEDIDO && !ejecutarCancelacionPedido(saga, caso)) {
            return saga;
        }
        if (saga.pasoPendiente() == PasoSaga.LIBERAR_STOCK && !ejecutarLiberacionStock(saga, caso)) {
            return saga;
        }
        return saga;
    }

    private boolean ejecutarCancelacionPedido(SagaCancelacion saga, Caso caso) {
        var comando = new PedidosPort.ComandoCancelarPedido(
                saga.pedidoId(),
                caso.motivo(),
                claveIdempotencia(saga, PasoSaga.CANCELAR_PEDIDO));

        ResultadoIntegracion resultado = pedidosPort.cancelarPedido(comando);

        return switch (resultado) {
            case ResultadoIntegracion.Exitoso exitoso -> {
                log.info("Saga {}: pedido {} cancelado (ref. {})", saga.id(), saga.pedidoId(),
                        exitoso.referenciaExterna());
                saga.pedidoCancelado(clock.instant());
                estadoWriter.persistirAvance(saga, caso);
                yield true;
            }
            case ResultadoIntegracion.FalloTransitorio fallo -> {
                log.warn("Saga {}: fallo transitorio cancelando el pedido {}: {}. Sin efectos aplicados.",
                        saga.id(), saga.pedidoId(), fallo.motivo());
                saga.registrarFalloTransitorio(fallo.motivo(), clock.instant());
                estadoWriter.persistirAvance(saga, caso);
                yield false;
            }
            case ResultadoIntegracion.FalloPermanente fallo -> {
                log.warn("Saga {}: Pedidos rechaza la cancelacion del pedido {} [{}]: {}",
                        saga.id(), saga.pedidoId(), fallo.codigo(), fallo.motivo());
                saga.registrarFalloPermanente(fallo.motivo(), clock.instant());
                estadoWriter.persistirAvance(saga, caso);
                yield false;
            }
        };
    }

    private boolean ejecutarLiberacionStock(SagaCancelacion saga, Caso caso) {
        List<LineaAfectada> lineas = caso.lineas();
        var comando = new InventarioPort.ComandoLiberarStock(
                saga.pedidoId(),
                lineas,
                claveIdempotencia(saga, PasoSaga.LIBERAR_STOCK));

        ResultadoIntegracion resultado = inventarioPort.liberarReserva(comando);

        return switch (resultado) {
            case ResultadoIntegracion.Exitoso exitoso -> {
                log.info("Saga {}: stock liberado para el pedido {} ({} lineas, ref. {})",
                        saga.id(), saga.pedidoId(), lineas.size(), exitoso.referenciaExterna());
                saga.stockLiberado(clock.instant());
                estadoWriter.persistirAvance(saga, caso);
                yield true;
            }
            case ResultadoIntegracion.FalloTransitorio fallo -> {
                saga.registrarFalloTransitorio(fallo.motivo(), clock.instant());
                estadoWriter.persistirAvance(saga, caso);
                if (saga.estado() == EstadoSaga.PENDIENTE_REINTENTO) {
                    log.warn("Saga {}: Inventario no disponible ({}). Pedido {} ya cancelado; "
                                    + "reintento {}/{} agendado para {}",
                            saga.id(), fallo.motivo(), saga.pedidoId(), saga.intentos(),
                            SagaCancelacion.maxIntentos(), saga.proximoIntentoEn());
                } else {
                    log.error("Saga {}: agotados los {} intentos contra Inventario ({}). "
                                    + "El pedido {} quedo cancelado y el stock sigue retenido: "
                                    + "se escala a backoffice.",
                            saga.id(), saga.intentos(), fallo.motivo(), saga.pedidoId());
                }
                yield false;
            }
            case ResultadoIntegracion.FalloPermanente fallo -> {
                saga.registrarFalloPermanente(fallo.motivo(), clock.instant());
                estadoWriter.persistirAvance(saga, caso);
                log.error("Saga {}: Inventario rechaza la liberacion de stock del pedido {} [{}]: {}. "
                                + "El pedido quedo cancelado: se escala a backoffice.",
                        saga.id(), saga.pedidoId(), fallo.codigo(), fallo.motivo());
                yield false;
            }
        };
    }

    private String claveIdempotencia(SagaCancelacion saga, PasoSaga paso) {
        return "%s:%s".formatted(saga.id(), paso.name().toLowerCase());
    }

    private ResultadoCancelacion vista(SagaCancelacion saga, Caso caso) {
        boolean requiereSeguimiento = !saga.estado().esTerminal() && saga.tieneEfectosAplicados();
        String detalle = switch (saga.estado()) {
            case COMPLETADA -> "Pedido cancelado y stock devuelto a disponible.";
            case FALLIDA -> "No se pudo cancelar el pedido. Ningun cambio fue aplicado: " + saga.ultimoError();
            case REQUIERE_INTERVENCION -> "El pedido fue cancelado, pero la devolucion de stock quedo pendiente "
                    + "de revision manual.";
            case PENDIENTE_REINTENTO -> "El pedido fue cancelado. La devolucion de stock se completara "
                    + "automaticamente (reintento en curso).";
            case INICIADA, PEDIDO_CANCELADO -> "Cancelacion en curso.";
        };
        return new ResultadoCancelacion(caso.id(), saga.id(), caso.estado(), saga.estado(),
                requiereSeguimiento, detalle);
    }
}
