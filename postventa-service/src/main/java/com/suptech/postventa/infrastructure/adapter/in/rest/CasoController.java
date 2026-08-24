package com.suptech.postventa.infrastructure.adapter.in.rest;

import com.suptech.postventa.domain.model.Caso;
import com.suptech.postventa.domain.port.in.ConsultarCasosQuery;
import com.suptech.postventa.domain.port.in.RegistrarCasoUseCase;
import com.suptech.postventa.domain.port.in.command.RegistrarCasoCommand;
import com.suptech.postventa.infrastructure.adapter.in.rest.dto.CasoResponse;
import com.suptech.postventa.infrastructure.adapter.in.rest.dto.RegistrarCasoRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/casos")
@RequiredArgsConstructor
public class CasoController {

    private final RegistrarCasoUseCase registrarCasoUseCase;
    private final ConsultarCasosQuery consultarCasosQuery;

    @PostMapping
    public ResponseEntity<CasoResponse> registrar(@Valid @RequestBody RegistrarCasoRequest peticion,
                                                  UriComponentsBuilder uriBuilder) {
        List<RegistrarCasoCommand.LineaCommand> lineas =
                (peticion.lineas() == null ? List.<RegistrarCasoRequest.LineaRequest>of() : peticion.lineas())
                        .stream()
                        .map(linea -> new RegistrarCasoCommand.LineaCommand(
                                linea.sku(), linea.cantidad(), linea.detalle()))
                        .toList();

        Caso caso = registrarCasoUseCase.registrar(new RegistrarCasoCommand(
                peticion.pedidoId(),
                peticion.clienteId(),
                peticion.tipo(),
                peticion.motivo(),
                peticion.montoSolicitado(),
                lineas));

        URI ubicacion = uriBuilder.path("/api/v1/casos/{id}").build(caso.id());
        return ResponseEntity.created(ubicacion).body(CasoResponse.desde(caso));
    }

    @GetMapping("/{casoId}")
    public CasoResponse porId(@PathVariable UUID casoId) {
        return CasoResponse.desde(consultarCasosQuery.porId(casoId));
    }

    @GetMapping
    public List<CasoResponse> porCliente(@RequestParam String clienteId) {
        return consultarCasosQuery.porCliente(clienteId).stream()
                .map(CasoResponse::desde)
                .toList();
    }
}
