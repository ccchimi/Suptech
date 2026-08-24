package com.suptech.postventa.infrastructure.adapter.out.client;

import com.suptech.postventa.domain.exception.ServicioExternoNoDisponibleException;
import com.suptech.postventa.domain.model.LineaAfectada;
import com.suptech.postventa.domain.model.ResultadoIntegracion;
import com.suptech.postventa.domain.port.out.PedidosPort;
import com.suptech.postventa.infrastructure.adapter.out.client.dto.PedidosApiDtos;
import com.suptech.postventa.infrastructure.config.RestClientConfig;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
public class PedidosRestClientAdapter implements PedidosPort {

    private static final String SERVICIO = "pedidos";

    private static final Set<String> ESTADOS_CANCELABLES =
            Set.of("CREADO", "CONFIRMADO", "PAGADO", "EN_PREPARACION");

    private final RestClient pedidosRestClient;

    public PedidosRestClientAdapter(@Qualifier("pedidosRestClient") RestClient pedidosRestClient) {
        this.pedidosRestClient = pedidosRestClient;
    }

    @Override
    @Retry(name = SERVICIO, fallbackMethod = "consultarPedidoFallback")
    @CircuitBreaker(name = SERVICIO)
    public Optional<PedidosPort.PedidoSnapshot> consultarPedido(String pedidoId) {
        try {
            return pedidosRestClient.get()
                    .uri("/api/v1/pedidos/{pedidoId}", pedidoId)
                    .exchange((peticion, respuesta) -> {
                        HttpStatusCode estado = respuesta.getStatusCode();
                        if (estado.value() == 404) {
                            return Optional.empty();
                        }
                        if (estado.is2xxSuccessful()) {
                            var cuerpo = respuesta.bodyTo(PedidosApiDtos.PedidoResponse.class);
                            return Optional.ofNullable(cuerpo).map(this::aSnapshot);
                        }
                        throw new IntegracionTransitoriaException(
                                "Pedidos respondio %s al consultar el pedido %s".formatted(estado, pedidoId));
                    });
        } catch (ResourceAccessException e) {
            throw new IntegracionTransitoriaException("No hay conexion con Pedidos: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unused")
    private Optional<PedidosPort.PedidoSnapshot> consultarPedidoFallback(String pedidoId, Throwable causa) {
        throw new ServicioExternoNoDisponibleException(SERVICIO, causa.getMessage());
    }

    @Override
    @Retry(name = SERVICIO, fallbackMethod = "cancelarPedidoFallback")
    @CircuitBreaker(name = SERVICIO)
    public ResultadoIntegracion cancelarPedido(ComandoCancelarPedido comando) {
        try {
            return pedidosRestClient.post()
                    .uri("/api/v1/pedidos/{pedidoId}/cancelacion", comando.pedidoId())
                    .header(RestClientConfig.CABECERA_IDEMPOTENCIA, comando.claveIdempotencia())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new PedidosApiDtos.CancelarPedidoRequest(comando.motivo(), "postventa-service"))
                    .exchange((peticion, respuesta) -> {
                        HttpStatusCode estado = respuesta.getStatusCode();

                        if (estado.is2xxSuccessful()) {
                            var cuerpo = respuesta.bodyTo(PedidosApiDtos.CancelarPedidoResponse.class);
                            return new ResultadoIntegracion.Exitoso(
                                    cuerpo == null ? null : cuerpo.referencia());
                        }
                        if (estado.value() == 409) {
                            log.info("Pedido {} ya estaba cancelado (respuesta idempotente)", comando.pedidoId());
                            return ResultadoIntegracion.Exitoso.sinReferencia();
                        }
                        if (estado.is4xxClientError()) {
                            return new ResultadoIntegracion.FalloPermanente(
                                    "HTTP_" + estado.value(),
                                    "Pedidos rechazo la cancelacion del pedido " + comando.pedidoId());
                        }
                        throw new IntegracionTransitoriaException(
                                "Pedidos respondio %s al cancelar el pedido %s".formatted(estado, comando.pedidoId()));
                    });
        } catch (ResourceAccessException e) {
            throw new IntegracionTransitoriaException("No hay conexion con Pedidos: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unused")
    private ResultadoIntegracion cancelarPedidoFallback(ComandoCancelarPedido comando, Throwable causa) {
        log.warn("Circuito/reintentos agotados cancelando el pedido {}: {}",
                comando.pedidoId(), causa.toString());
        return new ResultadoIntegracion.FalloTransitorio(causa.getMessage());
    }

    @Override
    @Retry(name = SERVICIO, fallbackMethod = "revertirCancelacionFallback")
    @CircuitBreaker(name = SERVICIO)
    public ResultadoIntegracion revertirCancelacion(String pedidoId, String claveIdempotencia) {
        return pedidosRestClient.post()
                .uri("/api/v1/pedidos/{pedidoId}/cancelacion/reversion", pedidoId)
                .header(RestClientConfig.CABECERA_IDEMPOTENCIA, claveIdempotencia)
                .exchange((peticion, respuesta) -> {
                    HttpStatusCode estado = respuesta.getStatusCode();
                    if (estado.is2xxSuccessful()) {
                        return ResultadoIntegracion.Exitoso.sinReferencia();
                    }
                    if (estado.is4xxClientError()) {
                        return new ResultadoIntegracion.FalloPermanente("HTTP_" + estado.value(),
                                "Pedidos no admite revertir la cancelacion del pedido " + pedidoId);
                    }
                    throw new IntegracionTransitoriaException(
                            "Pedidos respondio %s al revertir la cancelacion".formatted(estado));
                });
    }

    @SuppressWarnings("unused")
    private ResultadoIntegracion revertirCancelacionFallback(String pedidoId, String claveIdempotencia,
                                                             Throwable causa) {
        return new ResultadoIntegracion.FalloTransitorio(causa.getMessage());
    }

    private PedidosPort.PedidoSnapshot aSnapshot(PedidosApiDtos.PedidoResponse respuesta) {
        boolean cancelable = ESTADOS_CANCELABLES.contains(respuesta.estado());
        List<LineaAfectada> lineas = (respuesta.lineas() == null ? List.<PedidosApiDtos.LineaPedidoResponse>of()
                : respuesta.lineas())
                .stream()
                .map(linea -> LineaAfectada.de(linea.sku(), linea.cantidad()))
                .toList();

        return new PedidosPort.PedidoSnapshot(
                respuesta.pedidoId(),
                respuesta.clienteId(),
                respuesta.estado(),
                cancelable,
                cancelable ? null : "el pedido se encuentra en estado " + respuesta.estado(),
                lineas);
    }
}
