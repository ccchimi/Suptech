package com.suptech.postventa.domain.model;

import com.suptech.postventa.domain.exception.TransicionInvalidaException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class Caso {

    private static final int ESCALA_MONETARIA = 4;

    private final UUID id;
    private final String pedidoId;
    private final String clienteId;
    private final TipoCaso tipo;
    private final String motivo;
    private final BigDecimal montoSolicitado;
    private final List<LineaAfectada> lineas;
    private final Instant creadoEn;

    private EstadoCaso estado;
    private String resolucion;
    private Instant actualizadoEn;

    private Caso(UUID id, String pedidoId, String clienteId, TipoCaso tipo, String motivo,
                 BigDecimal montoSolicitado, List<LineaAfectada> lineas, EstadoCaso estado,
                 String resolucion, Instant creadoEn, Instant actualizadoEn) {
        this.id = Objects.requireNonNull(id);
        this.pedidoId = Objects.requireNonNull(pedidoId, "pedidoId es obligatorio");
        this.clienteId = Objects.requireNonNull(clienteId, "clienteId es obligatorio");
        this.tipo = Objects.requireNonNull(tipo, "tipo es obligatorio");
        this.motivo = Objects.requireNonNull(motivo, "motivo es obligatorio");
        this.montoSolicitado = montoSolicitado == null
                ? null
                : montoSolicitado.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        this.lineas = List.copyOf(lineas == null ? List.of() : lineas);
        this.estado = Objects.requireNonNull(estado);
        this.resolucion = resolucion;
        this.creadoEn = Objects.requireNonNull(creadoEn);
        this.actualizadoEn = Objects.requireNonNull(actualizadoEn);
        validarInvariantes();
    }

    public static Caso abrir(String pedidoId, String clienteId, TipoCaso tipo, String motivo,
                             BigDecimal montoSolicitado, List<LineaAfectada> lineas, Instant ahora) {
        return new Caso(UUID.randomUUID(), pedidoId, clienteId, tipo, motivo, montoSolicitado,
                lineas, EstadoCaso.RECIBIDO, null, ahora, ahora);
    }

    public static Caso rehidratar(UUID id, String pedidoId, String clienteId, TipoCaso tipo, String motivo,
                                  BigDecimal montoSolicitado, List<LineaAfectada> lineas, EstadoCaso estado,
                                  String resolucion, Instant creadoEn, Instant actualizadoEn) {
        return new Caso(id, pedidoId, clienteId, tipo, motivo, montoSolicitado, lineas, estado,
                resolucion, creadoEn, actualizadoEn);
    }

    private void validarInvariantes() {
        if (montoSolicitado != null && montoSolicitado.signum() < 0) {
            throw new IllegalArgumentException("El monto solicitado no puede ser negativo");
        }
        if (tipo == TipoCaso.REEMBOLSO && montoSolicitado == null) {
            throw new IllegalArgumentException("Un REEMBOLSO exige un monto solicitado");
        }
        if ((tipo == TipoCaso.DEVOLUCION || tipo == TipoCaso.CANCELACION) && lineas.isEmpty()) {
            throw new IllegalArgumentException("Un caso de tipo " + tipo + " exige al menos una linea");
        }
    }

    public void marcarEnProceso(Instant ahora) {
        transicionar(EstadoCaso.EN_PROCESO, null, ahora);
    }

    public void resolver(String resolucion, Instant ahora) {
        transicionar(EstadoCaso.RESUELTO, resolucion, ahora);
    }

    public void rechazar(String motivoRechazo, Instant ahora) {
        transicionar(EstadoCaso.RECHAZADO, motivoRechazo, ahora);
    }

    public void escalar(String detalle, Instant ahora) {
        transicionar(EstadoCaso.REQUIERE_INTERVENCION, detalle, ahora);
    }

    private void transicionar(EstadoCaso destino, String detalle, Instant ahora) {
        if (!estado.puedeTransicionarA(destino)) {
            throw new TransicionInvalidaException(id, estado, destino);
        }
        this.estado = destino;
        if (detalle != null) {
            this.resolucion = detalle;
        }
        this.actualizadoEn = ahora;
    }

    public UUID id() { return id; }
    public String pedidoId() { return pedidoId; }
    public String clienteId() { return clienteId; }
    public TipoCaso tipo() { return tipo; }
    public String motivo() { return motivo; }
    public BigDecimal montoSolicitado() { return montoSolicitado; }
    public List<LineaAfectada> lineas() { return lineas; }
    public EstadoCaso estado() { return estado; }
    public String resolucion() { return resolucion; }
    public Instant creadoEn() { return creadoEn; }
    public Instant actualizadoEn() { return actualizadoEn; }
}
