package com.suptech.postventa.application.service;

import com.suptech.postventa.domain.exception.CancelacionNoPermitidaException;
import com.suptech.postventa.domain.model.Caso;
import com.suptech.postventa.domain.model.EstadoCaso;
import com.suptech.postventa.domain.model.LineaAfectada;
import com.suptech.postventa.domain.model.ResultadoIntegracion;
import com.suptech.postventa.domain.model.saga.EstadoSaga;
import com.suptech.postventa.domain.model.saga.PasoSaga;
import com.suptech.postventa.domain.model.saga.SagaCancelacion;
import com.suptech.postventa.domain.port.in.SolicitarCancelacionUseCase.ResultadoCancelacion;
import com.suptech.postventa.domain.port.in.command.SolicitarCancelacionCommand;
import com.suptech.postventa.domain.port.out.CasoRepositoryPort;
import com.suptech.postventa.domain.port.out.InventarioPort;
import com.suptech.postventa.domain.port.out.PedidosPort;
import com.suptech.postventa.domain.port.out.SagaRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CancelacionSagaServiceTest {

    private static final String PEDIDO_ID = "PED-1001";
    private static final String CLIENTE_ID = "CLI-77";
    private static final Instant AHORA = Instant.parse("2026-08-23T10:00:00Z");

    private PedidosFake pedidos;
    private InventarioFake inventario;
    private CasoRepositorioFake casos;
    private SagaRepositorioFake sagas;
    private CancelacionSagaService servicio;

    @BeforeEach
    void prepararEscenario() {
        Clock reloj = Clock.fixed(AHORA, ZoneOffset.UTC);
        pedidos = new PedidosFake();
        inventario = new InventarioFake();
        casos = new CasoRepositorioFake();
        sagas = new SagaRepositorioFake();
        SagaEstadoWriter writer = new SagaEstadoWriter(casos, sagas, reloj);
        servicio = new CancelacionSagaService(pedidos, inventario, casos, sagas, writer, reloj);
    }

    private SolicitarCancelacionCommand comando() {
        return new SolicitarCancelacionCommand(PEDIDO_ID, CLIENTE_ID, "El cliente se arrepintio", "clave-1");
    }

    @Test
    @DisplayName("Camino feliz: pedido cancelado y stock liberado dejan la saga completada")
    void caminoFeliz() {
        ResultadoCancelacion resultado = servicio.solicitar(comando());

        assertThat(resultado.estadoSaga()).isEqualTo(EstadoSaga.COMPLETADA);
        assertThat(resultado.estadoCaso()).isEqualTo(EstadoCaso.RESUELTO);
        assertThat(resultado.requiereSeguimiento()).isFalse();
        assertThat(pedidos.cancelaciones).isEqualTo(1);
        assertThat(inventario.liberaciones).isEqualTo(1);
    }

    @Test
    @DisplayName("Las llamadas remotas viajan con una clave de idempotencia estable por paso")
    void propagaClaveDeIdempotencia() {
        servicio.solicitar(comando());

        UUID sagaId = sagas.almacen.values().iterator().next().id();
        assertThat(pedidos.ultimaClave).isEqualTo(sagaId + ":cancelar_pedido");
        assertThat(inventario.ultimaClave).isEqualTo(sagaId + ":liberar_stock");
    }

    @Nested
    @DisplayName("Cuando Pedidos responde bien pero Inventario esta caido")
    class InventarioCaido {

        @BeforeEach
        void inventarioNoResponde() {
            inventario.respuesta = new ResultadoIntegracion.FalloTransitorio("connection refused");
        }

        @Test
        @DisplayName("NO se revierte la cancelacion del pedido: se compensa hacia adelante")
        void noRevierteElPedido() {
            servicio.solicitar(comando());

            assertThat(pedidos.cancelaciones).isEqualTo(1);
            assertThat(pedidos.reversiones)
                    .as("des-cancelar un pedido ya confirmado al cliente seria peor que el propio fallo")
                    .isZero();
        }

        @Test
        @DisplayName("La saga queda pendiente de reintento, con backoff agendado y paso exacto")
        void agendaReintento() {
            ResultadoCancelacion resultado = servicio.solicitar(comando());

            SagaCancelacion saga = sagas.almacen.get(resultado.sagaId());
            assertThat(saga.estado()).isEqualTo(EstadoSaga.PENDIENTE_REINTENTO);
            assertThat(saga.pasoPendiente()).isEqualTo(PasoSaga.LIBERAR_STOCK);
            assertThat(saga.intentos()).isEqualTo(1);
            assertThat(saga.proximoIntentoEn()).isAfter(AHORA);
        }

        @Test
        @DisplayName("El caso sigue EN_PROCESO y se marca para seguimiento (respuesta 202)")
        void informaSeguimientoAlCliente() {
            ResultadoCancelacion resultado = servicio.solicitar(comando());

            assertThat(resultado.estadoCaso()).isEqualTo(EstadoCaso.EN_PROCESO);
            assertThat(resultado.requiereSeguimiento()).isTrue();
        }

        @Test
        @DisplayName("Cuando Inventario se recupera, el reconciliador cierra la saga sin repetir el paso 1")
        void elReconciliadorCompletaLaSaga() {
            ResultadoCancelacion resultado = servicio.solicitar(comando());
            SagaCancelacion saga = sagas.almacen.get(resultado.sagaId());
            Caso caso = casos.almacen.get(resultado.casoId());

            inventario.respuesta = new ResultadoIntegracion.Exitoso("LIB-9");
            servicio.avanzar(saga, caso);

            assertThat(saga.estado()).isEqualTo(EstadoSaga.COMPLETADA);
            assertThat(caso.estado()).isEqualTo(EstadoCaso.RESUELTO);
            assertThat(pedidos.cancelaciones).as("el paso ya completado no se repite").isEqualTo(1);
            assertThat(inventario.liberaciones).isEqualTo(2);
        }

        @Test
        @DisplayName("Agotados los reintentos la saga se escala a backoffice, nunca se descarta")
        void escalaTrasAgotarIntentos() {
            ResultadoCancelacion resultado = servicio.solicitar(comando());
            SagaCancelacion saga = sagas.almacen.get(resultado.sagaId());
            Caso caso = casos.almacen.get(resultado.casoId());

            for (int intento = saga.intentos(); intento < SagaCancelacion.maxIntentos(); intento++) {
                servicio.avanzar(saga, caso);
            }

            assertThat(saga.estado()).isEqualTo(EstadoSaga.REQUIERE_INTERVENCION);
            assertThat(caso.estado()).isEqualTo(EstadoCaso.REQUIERE_INTERVENCION);
            assertThat(caso.resolucion()).contains("backoffice");
        }
    }

    @Test
    @DisplayName("Si Pedidos rechaza la cancelacion, la saga muere limpia y no se toca Inventario")
    void falloPermanenteEnPedidos() {
        pedidos.respuestaCancelacion =
                new ResultadoIntegracion.FalloPermanente("HTTP_422", "el pedido ya fue despachado");

        ResultadoCancelacion resultado = servicio.solicitar(comando());

        assertThat(resultado.estadoSaga()).isEqualTo(EstadoSaga.FALLIDA);
        assertThat(resultado.estadoCaso()).isEqualTo(EstadoCaso.RECHAZADO);
        assertThat(resultado.requiereSeguimiento()).isFalse();
        assertThat(inventario.liberaciones).isZero();
    }

    @Test
    @DisplayName("Un pedido no cancelable se rechaza antes de producir ningun efecto")
    void pedidoNoCancelable() {
        pedidos.cancelable = false;

        assertThatThrownBy(() -> servicio.solicitar(comando()))
                .isInstanceOf(CancelacionNoPermitidaException.class);

        assertThat(pedidos.cancelaciones).isZero();
        assertThat(sagas.almacen).isEmpty();
    }

    @Test
    @DisplayName("Una segunda solicitud sobre el mismo pedido reutiliza la saga en curso")
    void solicitudDuplicadaEsIdempotente() {
        inventario.respuesta = new ResultadoIntegracion.FalloTransitorio("connection refused");

        ResultadoCancelacion primera = servicio.solicitar(comando());
        ResultadoCancelacion segunda = servicio.solicitar(comando());

        assertThat(segunda.sagaId()).isEqualTo(primera.sagaId());
        assertThat(segunda.casoId()).isEqualTo(primera.casoId());
        assertThat(pedidos.cancelaciones).as("el pedido no se cancela dos veces").isEqualTo(1);
        assertThat(inventario.liberaciones).isEqualTo(1);
    }

    private static final class PedidosFake implements PedidosPort {
        boolean cancelable = true;
        int cancelaciones;
        int reversiones;
        String ultimaClave;
        ResultadoIntegracion respuestaCancelacion = new ResultadoIntegracion.Exitoso("PED-CANC-1");

        @Override
        public Optional<PedidoSnapshot> consultarPedido(String pedidoId) {
            return Optional.of(new PedidoSnapshot(pedidoId, CLIENTE_ID,
                    cancelable ? "PAGADO" : "ENVIADO", cancelable,
                    cancelable ? null : "el pedido se encuentra en estado ENVIADO",
                    List.of(LineaAfectada.de("SKU-1", 2), LineaAfectada.de("SKU-2", 1))));
        }

        @Override
        public ResultadoIntegracion cancelarPedido(ComandoCancelarPedido comando) {
            ultimaClave = comando.claveIdempotencia();
            if (respuestaCancelacion.esExitoso()) {
                cancelaciones++;
            }
            return respuestaCancelacion;
        }

        @Override
        public ResultadoIntegracion revertirCancelacion(String pedidoId, String claveIdempotencia) {
            reversiones++;
            return ResultadoIntegracion.Exitoso.sinReferencia();
        }
    }

    private static final class InventarioFake implements InventarioPort {
        int liberaciones;
        String ultimaClave;
        ResultadoIntegracion respuesta = new ResultadoIntegracion.Exitoso("LIB-1");

        @Override
        public ResultadoIntegracion liberarReserva(ComandoLiberarStock comando) {
            ultimaClave = comando.claveIdempotencia();
            liberaciones++;
            return respuesta;
        }
    }

    private static final class CasoRepositorioFake implements CasoRepositoryPort {
        final Map<UUID, Caso> almacen = new LinkedHashMap<>();

        @Override
        public Caso guardar(Caso caso) {
            almacen.put(caso.id(), caso);
            return caso;
        }

        @Override
        public Optional<Caso> buscarPorId(UUID casoId) {
            return Optional.ofNullable(almacen.get(casoId));
        }

        @Override
        public List<Caso> buscarPorCliente(String clienteId) {
            return almacen.values().stream().filter(c -> c.clienteId().equals(clienteId)).toList();
        }
    }

    private static final class SagaRepositorioFake implements SagaRepositoryPort {
        final Map<UUID, SagaCancelacion> almacen = new LinkedHashMap<>();

        @Override
        public SagaCancelacion guardar(SagaCancelacion saga) {
            almacen.put(saga.id(), saga);
            return saga;
        }

        @Override
        public Optional<SagaCancelacion> buscarPorId(UUID sagaId) {
            return Optional.ofNullable(almacen.get(sagaId));
        }

        @Override
        public Optional<SagaCancelacion> buscarActivaPorPedido(String pedidoId) {
            return almacen.values().stream()
                    .filter(saga -> saga.pedidoId().equals(pedidoId))
                    .filter(saga -> saga.estado() != EstadoSaga.COMPLETADA
                            && saga.estado() != EstadoSaga.FALLIDA)
                    .findFirst();
        }

        @Override
        public List<SagaCancelacion> buscarReintentosVencidos(Instant limite, int maxResultados) {
            List<SagaCancelacion> vencidas = new ArrayList<>();
            for (SagaCancelacion saga : almacen.values()) {
                if (saga.estado() == EstadoSaga.PENDIENTE_REINTENTO
                        && saga.proximoIntentoEn() != null
                        && !saga.proximoIntentoEn().isAfter(limite)) {
                    vencidas.add(saga);
                }
            }
            return vencidas.stream().limit(maxResultados).toList();
        }
    }
}
