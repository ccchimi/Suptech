package com.suptech.postventa.infrastructure.adapter.out.client;

import com.suptech.postventa.domain.model.ResultadoIntegracion;
import com.suptech.postventa.domain.port.out.InventarioPort;
import com.suptech.postventa.infrastructure.adapter.out.client.dto.InventarioApiDtos;
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

@Slf4j
@Component
public class InventarioRestClientAdapter implements InventarioPort {

    private static final String SERVICIO = "inventario";

    private final RestClient inventarioRestClient;

    public InventarioRestClientAdapter(@Qualifier("inventarioRestClient") RestClient inventarioRestClient) {
        this.inventarioRestClient = inventarioRestClient;
    }

    @Override
    @Retry(name = SERVICIO, fallbackMethod = "liberarReservaFallback")
    @CircuitBreaker(name = SERVICIO)
    public ResultadoIntegracion liberarReserva(ComandoLiberarStock comando) {
        List<InventarioApiDtos.ItemRequest> items = comando.lineas().stream()
                .map(linea -> new InventarioApiDtos.ItemRequest(linea.sku(), linea.cantidad()))
                .toList();

        var peticion = new InventarioApiDtos.LiberarStockRequest(
                comando.pedidoId(), "CANCELACION_POSTVENTA", items);

        try {
            return inventarioRestClient.post()
                    .uri("/api/v1/reservas/liberaciones")
                    .header(RestClientConfig.CABECERA_IDEMPOTENCIA, comando.claveIdempotencia())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(peticion)
                    .exchange((request, respuesta) -> {
                        HttpStatusCode estado = respuesta.getStatusCode();

                        if (estado.is2xxSuccessful()) {
                            var cuerpo = respuesta.bodyTo(InventarioApiDtos.LiberarStockResponse.class);
                            return new ResultadoIntegracion.Exitoso(cuerpo == null ? null : cuerpo.referencia());
                        }
                        if (estado.value() == 409) {
                            log.info("Inventario ya habia liberado el stock del pedido {}", comando.pedidoId());
                            return ResultadoIntegracion.Exitoso.sinReferencia();
                        }
                        if (estado.is4xxClientError()) {
                            return new ResultadoIntegracion.FalloPermanente(
                                    "HTTP_" + estado.value(),
                                    "Inventario rechazo la liberacion de stock del pedido " + comando.pedidoId());
                        }
                        throw new IntegracionTransitoriaException(
                                "Inventario respondio %s al liberar el stock del pedido %s"
                                        .formatted(estado, comando.pedidoId()));
                    });
        } catch (ResourceAccessException e) {
            throw new IntegracionTransitoriaException("No hay conexion con Inventario: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unused")
    private ResultadoIntegracion liberarReservaFallback(ComandoLiberarStock comando, Throwable causa) {
        log.warn("Circuito/reintentos agotados liberando stock del pedido {}: {}",
                comando.pedidoId(), causa.toString());
        return new ResultadoIntegracion.FalloTransitorio(causa.getMessage());
    }
}
