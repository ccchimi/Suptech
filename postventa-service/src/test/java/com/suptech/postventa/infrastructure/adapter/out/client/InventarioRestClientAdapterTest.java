package com.suptech.postventa.infrastructure.adapter.out.client;

import com.suptech.postventa.domain.model.LineaAfectada;
import com.suptech.postventa.domain.model.ResultadoIntegracion;
import com.suptech.postventa.domain.port.out.InventarioPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class InventarioRestClientAdapterTest {

    private static final String BASE = "http://inventario.test";
    private static final String RUTA = BASE + "/api/v1/reservas/liberaciones";

    private MockRestServiceServer servidor;
    private InventarioRestClientAdapter adaptador;

    @BeforeEach
    void prepararCliente() {
        RestClient.Builder constructor = RestClient.builder().baseUrl(BASE);
        servidor = MockRestServiceServer.bindTo(constructor).build();
        adaptador = new InventarioRestClientAdapter(constructor.build());
    }

    private static InventarioPort.ComandoLiberarStock comando() {
        return new InventarioPort.ComandoLiberarStock("PED-1001",
                List.of(LineaAfectada.de("SKU-1", 2), LineaAfectada.de("SKU-2", 1)),
                "saga-1:liberar_stock");
    }

    @Test
    @DisplayName("La liberacion envia los items y la clave de idempotencia")
    void liberacionExitosa() {
        servidor.expect(requestTo(RUTA))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "saga-1:liberar_stock"))
                .andExpect(jsonPath("$.pedidoId").value("PED-1001"))
                .andExpect(jsonPath("$.motivo").value("CANCELACION_POSTVENTA"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].sku").value("SKU-1"))
                .andExpect(jsonPath("$.items[0].cantidad").value(2))
                .andRespond(withSuccess("""
                        {"referencia": "LIB-9", "estado": "LIBERADO"}
                        """, MediaType.APPLICATION_JSON));

        ResultadoIntegracion resultado = adaptador.liberarReserva(comando());

        assertThat(resultado).isInstanceOf(ResultadoIntegracion.Exitoso.class);
        assertThat(((ResultadoIntegracion.Exitoso) resultado).referenciaExterna()).isEqualTo("LIB-9");
        servidor.verify();
    }

    @Test
    @DisplayName("Un 409 indica que ya se libero con esa clave: es exito idempotente")
    void liberacionRepetidaEsExito() {
        servidor.expect(requestTo(RUTA)).andRespond(withStatus(HttpStatus.CONFLICT));

        assertThat(adaptador.liberarReserva(comando()).esExitoso()).isTrue();
    }

    @Test
    @DisplayName("Un rechazo de negocio es fallo permanente")
    void rechazoEsPermanente() {
        servidor.expect(requestTo(RUTA)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        ResultadoIntegracion resultado = adaptador.liberarReserva(comando());

        assertThat(resultado).isInstanceOf(ResultadoIntegracion.FalloPermanente.class);
        assertThat(((ResultadoIntegracion.FalloPermanente) resultado).codigo()).isEqualTo("HTTP_400");
    }

    @Test
    @DisplayName("Inventario caido se propaga como excepcion transitoria y la saga reintentara")
    void servicioCaidoEsTransitorio() {
        servidor.expect(requestTo(RUTA)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> adaptador.liberarReserva(comando()))
                .isInstanceOf(IntegracionTransitoriaException.class);
    }
}
