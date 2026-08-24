package com.suptech.postventa.infrastructure.adapter.in.rest;

import com.suptech.postventa.domain.exception.CancelacionNoPermitidaException;
import com.suptech.postventa.domain.exception.CasoNoEncontradoException;
import com.suptech.postventa.domain.exception.DominioException;
import com.suptech.postventa.domain.exception.PedidoNoEncontradoException;
import com.suptech.postventa.domain.exception.ServicioExternoNoDisponibleException;
import com.suptech.postventa.domain.exception.TransicionInvalidaException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class ManejadorGlobalErrores extends ResponseEntityExceptionHandler {

    private static final String BASE_TIPO = "https://api.suptech.com/errores/";

    @ExceptionHandler(DominioException.class)
    ProblemDetail manejarDominio(DominioException excepcion) {
        HttpStatus estado = switch (excepcion) {
            case CasoNoEncontradoException ignored -> HttpStatus.NOT_FOUND;
            case PedidoNoEncontradoException ignored -> HttpStatus.NOT_FOUND;
            case CancelacionNoPermitidaException ignored -> HttpStatus.CONFLICT;
            case TransicionInvalidaException ignored -> HttpStatus.CONFLICT;
            case ServicioExternoNoDisponibleException ignored -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_REQUEST;
        };

        if (estado.is5xxServerError()) {
            log.error("Error de integracion: {}", excepcion.getMessage());
        } else {
            log.warn("Peticion rechazada [{}]: {}", excepcion.codigo(), excepcion.getMessage());
        }

        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, excepcion.getMessage());
        problema.setTitle(excepcion.codigo());
        problema.setType(URI.create(BASE_TIPO + excepcion.codigo().toLowerCase().replace('_', '-')));
        return problema;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException excepcion,
                                                                  HttpHeaders cabeceras,
                                                                  HttpStatusCode estado,
                                                                  WebRequest peticion) {
        Map<String, String> errores = excepcion.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        error -> error.getField(),
                        error -> error.getDefaultMessage() == null ? "valor invalido" : error.getDefaultMessage(),
                        (primero, segundo) -> primero));

        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "La peticion contiene campos invalidos");
        problema.setTitle("VALIDACION_FALLIDA");
        problema.setType(URI.create(BASE_TIPO + "validacion-fallida"));
        problema.setProperty("errores", errores);

        return handleExceptionInternal(excepcion, problema, cabeceras, HttpStatus.BAD_REQUEST, peticion);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail manejarInesperado(Exception excepcion) {
        log.error("Error inesperado", excepcion);
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servicio de postventa");
        problema.setTitle("ERROR_INTERNO");
        return problema;
    }
}
