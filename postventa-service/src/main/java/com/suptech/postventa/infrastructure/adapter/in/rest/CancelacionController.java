package com.suptech.postventa.infrastructure.adapter.in.rest;

import com.suptech.postventa.domain.port.in.SolicitarCancelacionUseCase;
import com.suptech.postventa.domain.port.in.SolicitarCancelacionUseCase.ResultadoCancelacion;
import com.suptech.postventa.domain.port.in.command.SolicitarCancelacionCommand;
import com.suptech.postventa.infrastructure.adapter.in.rest.dto.CancelacionResponse;
import com.suptech.postventa.infrastructure.adapter.in.rest.dto.SolicitarCancelacionRequest;
import com.suptech.postventa.infrastructure.config.RestClientConfig;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cancelaciones")
@RequiredArgsConstructor
public class CancelacionController {

    private final SolicitarCancelacionUseCase solicitarCancelacionUseCase;

    @PostMapping
    public ResponseEntity<CancelacionResponse> cancelar(
            @Valid @RequestBody SolicitarCancelacionRequest peticion,
            @RequestHeader(name = RestClientConfig.CABECERA_IDEMPOTENCIA, required = false)
            String claveIdempotencia) {

        var comando = new SolicitarCancelacionCommand(
                peticion.pedidoId(),
                peticion.clienteId(),
                peticion.motivo(),
                claveIdempotencia != null ? claveIdempotencia : UUID.randomUUID().toString());

        ResultadoCancelacion resultado = solicitarCancelacionUseCase.solicitar(comando);

        HttpStatus estado = switch (resultado.estadoSaga()) {
            case COMPLETADA -> HttpStatus.OK;
            case FALLIDA -> HttpStatus.CONFLICT;
            case INICIADA, PEDIDO_CANCELADO, PENDIENTE_REINTENTO, REQUIERE_INTERVENCION -> HttpStatus.ACCEPTED;
        };

        return ResponseEntity.status(estado).body(CancelacionResponse.desde(resultado));
    }
}
