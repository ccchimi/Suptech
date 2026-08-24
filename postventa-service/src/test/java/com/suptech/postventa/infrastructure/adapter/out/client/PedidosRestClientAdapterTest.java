package com.suptech.postventa.infrastructure.adapter.out.client;

import com.suptech.postventa.domain.model.ResultadoIntegracion;
import com.suptech.postventa.domain.port.out.PedidosPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PedidosRestClientAdapterTest {

    private static final String BASE = "http://pedidos.test";
    private static final String PEDIDO_ID = "PED-1001";

    private MockRestServiceServer servidor;
    private PedidosRestClientAdapter adaptador;

    @BeforeEach
    void prepararCliente() {
        RestClient.Builder constructor = RestClient.builder().baseUrl(BASE);
        servidor = MockRestServiceServer.bindTo(constructor).build();
        adaptador = new PedidosRestClientAdapter(constructor.build());
    }

    @Test
    @DisplayName("Un pedido en estado cancelable se traduce a snapshot con sus lineas")
    void pedidoCancelable() {
        servidor.expect(requestTo(BASE + "/api/v1/pedidos/" + PEDIDO_ID))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "pedidoId": "PED-1001",
                          "clienteId": "CLI-77",
                          "estado": "PAGADO",
                          "lineas": [{"sku": "SKU-1", "cantidad": 2}, {"sku": "SKU-2", "cantidad": 1}]
                        }
                        """, MediaType.APPLICATION_JSON));

        Optional<PedidosPort.PedidoSnapshot> snapshot = adaptador.consultarPedido(PEDIDO_ID);

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().cancelable()).isTrue();
        assertThat(snapshot.get().lineas()).hasSize(2);
        assertThat(snapshot.get().lineas().getFirst().sku()).isEqualTo("SKU-1");
        servidor.verify();
    }

    @Test
    @DisplayName("Un pedido ya enviado se marca como no cancelable con su motivo")
    void pedidoNoCancelable() {
        servidor.expect(requestTo(BASE + "/api/v1/pedidos/" + PEDIDO_ID))
                .andRespond(withSuccess("""
                        {"pedidoId": "PED-1001", "clienteId": "CLI-77", "estado": "ENVIADO", "lineas": []}
                        """, MediaType.APPLICATION_JSON));

        PedidosPort.PedidoSnapshot snapshot = adaptador.consultarPedido(PEDIDO_ID).orElseThrow();

        assertThat(snapshot.cancelable()).isFalse();
        assertThat(snapshot.motivoNoCancelable()).contains("ENVIADO");
    }

    @Test
    @DisplayName("Un 404 devuelve vacio, no un error")
    void pedidoInexistente() {
        servidor.expect(requestTo(BASE + "/api/v1/pedidos/" + PEDIDO_ID))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(adaptador.consultarPedido(PEDIDO_ID)).isEmpty();
    }

    @Test
    @DisplayName("La cancelacion viaja con la clave de idempotencia y el motivo")
    void cancelacionExitosa() {
        servidor.expect(requestTo(BASE + "/api/v1/pedidos/" + PEDIDO_ID + "/cancelacion"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "saga-1:cancelar_pedido"))
                .andExpect(jsonPath("$.motivo").value("arrepentimiento"))
                .andExpect(jsonPath("$.solicitadoPor").value("postventa-service"))
                .andRespond(withSuccess("""
                        {"pedidoId": "PED-1001", "estado": "CANCELADO", "referencia": "REF-9"}
                        """, MediaType.APPLICATION_JSON));

        ResultadoIntegracion resultado = adaptador.cancelarPedido(
                new PedidosPort.ComandoCancelarPedido(PEDIDO_ID, "arrepentimiento", "saga-1:cancelar_pedido"));

        assertThat(resultado).isInstanceOf(ResultadoIntegracion.Exitoso.class);
        assertThat(((ResultadoIntegracion.Exitoso) resultado).referenciaExterna()).isEqualTo("REF-9");
        servidor.verify();
    }

    @Test
    @DisplayName("Un 409 significa que ya estaba cancelado: es exito idempotente")
    void cancelacionYaAplicadaEsExito() {
        servidor.expect(requestTo(BASE + "/api/v1/pedidos/" + PEDIDO_ID + "/cancelacion"))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        ResultadoIntegracion resultado = adaptador.cancelarPedido(
                new PedidosPort.ComandoCancelarPedido(PEDIDO_ID, "motivo", "clave"));

        assertThat(resultado.esExitoso()).isTrue();
    }

    @Test
    @DisplayName("Un 4xx de negocio es fallo permanente: reintentar no cambia nada")
    void rechazoDeNegocioEsPermanente() {
        servidor.expect(requestTo(BASE + "/api/v1/pedidos/" + PEDIDO_ID + "/cancelacion"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));

        ResultadoIntegracion resultado = adaptador.cancelarPedido(
                new PedidosPort.ComandoCancelarPedido(PEDIDO_ID, "motivo", "clave"));

        assertThat(resultado).isInstanceOf(ResultadoIntegracion.FalloPermanente.class);
        assertThat(((ResultadoIntegracion.FalloPermanente) resultado).codigo()).isEqualTo("HTTP_422");
    }

    @Test
    @DisplayName("Un 5xx se propaga como excepcion transitoria para que Resilience4j reintente")
    void errorDeServidorEsTransitorio() {
        servidor.expect(requestTo(BASE + "/api/v1/pedidos/" + PEDIDO_ID + "/cancelacion"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> adaptador.cancelarPedido(
                new PedidosPort.ComandoCancelarPedido(PEDIDO_ID, "motivo", "clave")))
                .isInstanceOf(IntegracionTransitoriaException.class);
    }
}
