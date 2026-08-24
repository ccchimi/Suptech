package com.suptech.postventa.domain.model.saga;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class SagaCancelacion {

    private static final int MAX_INTENTOS = 6;
    private static final Duration BACKOFF_BASE = Duration.ofSeconds(15);
    private static final Duration BACKOFF_MAXIMO = Duration.ofMinutes(30);

    private final UUID id;
    private final UUID casoId;
    private final String pedidoId;
    private final Instant creadoEn;

    private EstadoSaga estado;
    private PasoSaga pasoPendiente;
    private int intentos;
    private Instant proximoIntentoEn;
    private String ultimoError;
    private Instant actualizadoEn;

    private SagaCancelacion(UUID id, UUID casoId, String pedidoId, EstadoSaga estado, PasoSaga pasoPendiente,
                            int intentos, Instant proximoIntentoEn, String ultimoError,
                            Instant creadoEn, Instant actualizadoEn) {
        this.id = Objects.requireNonNull(id);
        this.casoId = Objects.requireNonNull(casoId);
        this.pedidoId = Objects.requireNonNull(pedidoId);
        this.estado = Objects.requireNonNull(estado);
        this.pasoPendiente = Objects.requireNonNull(pasoPendiente);
        this.intentos = intentos;
        this.proximoIntentoEn = proximoIntentoEn;
        this.ultimoError = ultimoError;
        this.creadoEn = Objects.requireNonNull(creadoEn);
        this.actualizadoEn = Objects.requireNonNull(actualizadoEn);
    }

    public static SagaCancelacion iniciar(UUID casoId, String pedidoId, Instant ahora) {
        return new SagaCancelacion(UUID.randomUUID(), casoId, pedidoId, EstadoSaga.INICIADA,
                PasoSaga.CANCELAR_PEDIDO, 0, null, null, ahora, ahora);
    }

    public static SagaCancelacion rehidratar(UUID id, UUID casoId, String pedidoId, EstadoSaga estado,
                                             PasoSaga pasoPendiente, int intentos, Instant proximoIntentoEn,
                                             String ultimoError, Instant creadoEn, Instant actualizadoEn) {
        return new SagaCancelacion(id, casoId, pedidoId, estado, pasoPendiente, intentos,
                proximoIntentoEn, ultimoError, creadoEn, actualizadoEn);
    }

    public void pedidoCancelado(Instant ahora) {
        this.estado = EstadoSaga.PEDIDO_CANCELADO;
        this.pasoPendiente = PasoSaga.LIBERAR_STOCK;
        this.intentos = 0;
        this.proximoIntentoEn = null;
        this.ultimoError = null;
        this.actualizadoEn = ahora;
    }

    public void stockLiberado(Instant ahora) {
        this.estado = EstadoSaga.COMPLETADA;
        this.pasoPendiente = PasoSaga.NINGUNO;
        this.proximoIntentoEn = null;
        this.ultimoError = null;
        this.actualizadoEn = ahora;
    }

    public void registrarFalloTransitorio(String motivo, Instant ahora) {
        this.intentos++;
        this.ultimoError = motivo;
        this.actualizadoEn = ahora;

        if (this.intentos >= MAX_INTENTOS) {
            this.estado = desenlaceAlAgotarIntentos();
            this.proximoIntentoEn = null;
            return;
        }
        this.estado = EstadoSaga.PENDIENTE_REINTENTO;
        this.proximoIntentoEn = ahora.plus(calcularBackoff());
    }

    public void registrarFalloPermanente(String motivo, Instant ahora) {
        this.ultimoError = motivo;
        this.actualizadoEn = ahora;
        this.proximoIntentoEn = null;
        this.estado = desenlaceAlAgotarIntentos();
    }

    private EstadoSaga desenlaceAlAgotarIntentos() {
        return switch (pasoPendiente) {
            case CANCELAR_PEDIDO -> EstadoSaga.FALLIDA;
            case LIBERAR_STOCK, NINGUNO -> EstadoSaga.REQUIERE_INTERVENCION;
        };
    }

    private Duration calcularBackoff() {
        Duration calculado = BACKOFF_BASE.multipliedBy(1L << (intentos - 1));
        return calculado.compareTo(BACKOFF_MAXIMO) > 0 ? BACKOFF_MAXIMO : calculado;
    }

    public boolean tieneEfectosAplicados() {
        return estado != EstadoSaga.INICIADA && pasoPendiente != PasoSaga.CANCELAR_PEDIDO;
    }

    public UUID id() { return id; }
    public UUID casoId() { return casoId; }
    public String pedidoId() { return pedidoId; }
    public EstadoSaga estado() { return estado; }
    public PasoSaga pasoPendiente() { return pasoPendiente; }
    public int intentos() { return intentos; }
    public Instant proximoIntentoEn() { return proximoIntentoEn; }
    public String ultimoError() { return ultimoError; }
    public Instant creadoEn() { return creadoEn; }
    public Instant actualizadoEn() { return actualizadoEn; }

    public static int maxIntentos() { return MAX_INTENTOS; }
}
