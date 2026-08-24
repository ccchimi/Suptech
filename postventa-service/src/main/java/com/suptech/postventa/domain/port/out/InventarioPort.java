package com.suptech.postventa.domain.port.out;

import com.suptech.postventa.domain.model.LineaAfectada;
import com.suptech.postventa.domain.model.ResultadoIntegracion;

import java.util.List;

public interface InventarioPort {

    ResultadoIntegracion liberarReserva(ComandoLiberarStock comando);

    record ComandoLiberarStock(String pedidoId, List<LineaAfectada> lineas, String claveIdempotencia) { }
}
