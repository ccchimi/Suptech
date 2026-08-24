package com.suptech.postventa.infrastructure.adapter.in.rest;

import com.suptech.postventa.domain.exception.CasoNoEncontradoException;
import com.suptech.postventa.domain.model.Caso;
import com.suptech.postventa.domain.model.LineaAfectada;
import com.suptech.postventa.domain.model.TipoCaso;
import com.suptech.postventa.domain.port.in.ConsultarCasosQuery;
import com.suptech.postventa.domain.port.in.RegistrarCasoUseCase;
import com.suptech.postventa.domain.port.in.command.RegistrarCasoCommand;
import com.suptech.postventa.infrastructure.config.SeguridadConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CasoController.class)
@Import(SeguridadConfig.class)
class CasoControllerTest {

    private static final String CUERPO_VALIDO = """
            {
              "pedidoId": "PED-1001",
              "clienteId": "CLI-77",
              "tipo": "REEMBOLSO",
              "motivo": "Producto defectuoso",
              "montoSolicitado": 149.90,
              "lineas": [{"sku": "SKU-1", "cantidad": 1}]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegistrarCasoUseCase registrarCasoUseCase;

    @MockitoBean
    private ConsultarCasosQuery consultarCasosQuery;

    private static Caso casoDeEjemplo() {
        return Caso.abrir("PED-1001", "CLI-77", TipoCaso.REEMBOLSO, "Producto defectuoso",
                new BigDecimal("149.90"), List.of(LineaAfectada.de("SKU-1", 1)),
                Instant.parse("2026-08-23T10:00:00Z"));
    }

    @Test
    @WithMockUser(roles = "AGENTE")
    @DisplayName("Registrar un caso devuelve 201 con la cabecera Location")
    void registrarDevuelve201() throws Exception {
        Caso caso = casoDeEjemplo();
        given(registrarCasoUseCase.registrar(any(RegistrarCasoCommand.class))).willReturn(caso);

        mockMvc.perform(post("/api/v1/casos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/casos/" + caso.id()))
                .andExpect(jsonPath("$.id").value(caso.id().toString()))
                .andExpect(jsonPath("$.estado").value("RECIBIDO"))
                .andExpect(jsonPath("$.lineas[0].sku").value("SKU-1"));
    }

    @Test
    @WithMockUser(roles = "AGENTE")
    @DisplayName("Una peticion invalida devuelve 400 con el detalle campo a campo")
    void validacionDevuelveDetallePorCampo() throws Exception {
        String invalido = """
                {"pedidoId": "", "clienteId": "CLI-77", "tipo": "REEMBOLSO",
                 "motivo": "", "montoSolicitado": -5}
                """;

        mockMvc.perform(post("/api/v1/casos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalido))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("VALIDACION_FALLIDA"))
                .andExpect(jsonPath("$.errores.pedidoId").value("pedidoId es obligatorio"))
                .andExpect(jsonPath("$.errores.montoSolicitado").value("el monto no puede ser negativo"));

        verifyNoInteractions(registrarCasoUseCase);
    }

    @Test
    @WithMockUser(roles = "AGENTE")
    @DisplayName("Un caso inexistente devuelve 404 como ProblemDetail")
    void casoInexistenteDevuelve404() throws Exception {
        UUID casoId = UUID.randomUUID();
        given(consultarCasosQuery.porId(casoId)).willThrow(new CasoNoEncontradoException(casoId));

        mockMvc.perform(get("/api/v1/casos/{casoId}", casoId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("CASO_NO_ENCONTRADO"))
                .andExpect(jsonPath("$.type").value("https://api.suptech.com/errores/caso-no-encontrado"));
    }

    @Test
    @WithMockUser(roles = "AGENTE")
    @DisplayName("La consulta por cliente devuelve la lista de casos")
    void consultaPorClienteDevuelveLista() throws Exception {
        given(consultarCasosQuery.porCliente("CLI-77")).willReturn(List.of(casoDeEjemplo()));

        mockMvc.perform(get("/api/v1/casos").param("clienteId", "CLI-77"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].clienteId").value("CLI-77"));
    }

    @Test
    @DisplayName("Sin credenciales la API responde 401 como ProblemDetail")
    void sinCredencialesDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/casos").param("clienteId", "CLI-77"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("NO_AUTENTICADO"));

        verifyNoInteractions(consultarCasosQuery);
    }
}
