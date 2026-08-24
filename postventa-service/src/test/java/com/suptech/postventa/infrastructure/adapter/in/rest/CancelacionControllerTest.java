package com.suptech.postventa.infrastructure.adapter.in.rest;

import com.suptech.postventa.domain.exception.CancelacionNoPermitidaException;
import com.suptech.postventa.domain.exception.ServicioExternoNoDisponibleException;
import com.suptech.postventa.domain.model.EstadoCaso;
import com.suptech.postventa.domain.model.saga.EstadoSaga;
import com.suptech.postventa.domain.port.in.SolicitarCancelacionUseCase;
import com.suptech.postventa.domain.port.in.SolicitarCancelacionUseCase.ResultadoCancelacion;
import com.suptech.postventa.domain.port.in.command.SolicitarCancelacionCommand;
import com.suptech.postventa.infrastructure.config.SeguridadConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CancelacionController.class)
@Import(SeguridadConfig.class)
class CancelacionControllerTest {

    private static final String CUERPO = """
            {"pedidoId": "PED-1001", "clienteId": "CLI-77", "motivo": "El cliente se arrepintio"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SolicitarCancelacionUseCase solicitarCancelacionUseCase;

    private static ResultadoCancelacion resultado(EstadoSaga estadoSaga, EstadoCaso estadoCaso,
                                                  boolean requiereSeguimiento) {
        return new ResultadoCancelacion(UUID.randomUUID(), UUID.randomUUID(), estadoCaso, estadoSaga,
                requiereSeguimiento, "detalle");
    }

    @Test
    @WithMockUser(roles = "AGENTE")
    @DisplayName("Saga completada responde 200")
    void sagaCompletadaDevuelve200() throws Exception {
        given(solicitarCancelacionUseCase.solicitar(any()))
                .willReturn(resultado(EstadoSaga.COMPLETADA, EstadoCaso.RESUELTO, false));

        mockMvc.perform(post("/api/v1/cancelaciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoSaga").value("COMPLETADA"));
    }

    @Test
    @WithMockUser(roles = "AGENTE")
    @DisplayName("Con un paso pendiente responde 202 e indica donde seguir el caso")
    void sagaPendienteDevuelve202() throws Exception {
        ResultadoCancelacion pendiente =
                resultado(EstadoSaga.PENDIENTE_REINTENTO, EstadoCaso.EN_PROCESO, true);
        given(solicitarCancelacionUseCase.solicitar(any())).willReturn(pendiente);

        mockMvc.perform(post("/api/v1/cancelaciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.estadoSaga").value("PENDIENTE_REINTENTO"))
                .andExpect(jsonPath("$.seguimiento").value("/api/v1/casos/" + pendiente.casoId()));
    }

    @Test
    @WithMockUser(roles = "AGENTE")
    @DisplayName("Una saga fallida responde 409: no se aplico ningun cambio")
    void sagaFallidaDevuelve409() throws Exception {
        given(solicitarCancelacionUseCase.solicitar(any()))
                .willReturn(resultado(EstadoSaga.FALLIDA, EstadoCaso.RECHAZADO, false));

        mockMvc.perform(post("/api/v1/cancelaciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "AGENTE")
    @DisplayName("Un pedido no cancelable responde 409 con el codigo de negocio")
    void pedidoNoCancelableDevuelve409() throws Exception {
        given(solicitarCancelacionUseCase.solicitar(any()))
                .willThrow(new CancelacionNoPermitidaException("PED-1001", "ya fue despachado"));

        mockMvc.perform(post("/api/v1/cancelaciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("CANCELACION_NO_PERMITIDA"));
    }

    @Test
    @WithMockUser(roles = "AGENTE")
    @DisplayName("Si Pedidos no responde, la API devuelve 503")
    void servicioCaidoDevuelve503() throws Exception {
        given(solicitarCancelacionUseCase.solicitar(any()))
                .willThrow(new ServicioExternoNoDisponibleException("pedidos", "connection refused"));

        mockMvc.perform(post("/api/v1/cancelaciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("SERVICIO_EXTERNO_NO_DISPONIBLE"));
    }

    @Test
    @WithMockUser(roles = "AGENTE")
    @DisplayName("La cabecera Idempotency-Key llega intacta al caso de uso")
    void propagaLaClaveDeIdempotencia() throws Exception {
        given(solicitarCancelacionUseCase.solicitar(any()))
                .willReturn(resultado(EstadoSaga.COMPLETADA, EstadoCaso.RESUELTO, false));

        mockMvc.perform(post("/api/v1/cancelaciones")
                        .header("Idempotency-Key", "clave-del-cliente")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isOk());

        ArgumentCaptor<SolicitarCancelacionCommand> capturador =
                ArgumentCaptor.forClass(SolicitarCancelacionCommand.class);
        then(solicitarCancelacionUseCase).should().solicitar(capturador.capture());
        assertThat(capturador.getValue().claveIdempotencia()).isEqualTo("clave-del-cliente");
    }

    @Test
    @WithMockUser(roles = "CONSULTA")
    @DisplayName("Un rol sin permiso de cancelacion recibe 403")
    void rolSinPermisoDevuelve403() throws Exception {
        mockMvc.perform(post("/api/v1/cancelaciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("ACCESO_DENEGADO"));

        verifyNoInteractions(solicitarCancelacionUseCase);
    }
}
