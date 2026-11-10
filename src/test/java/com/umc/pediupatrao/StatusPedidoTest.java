package com.umc.pediupatrao;

import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusPedidoTest {

    @Test
    void fluxoDeEntregaPermitido() {
        assertTrue(StatusPedido.RECEBIDO.podeTransitarPara(StatusPedido.EM_PREPARACAO, TipoEntrega.ENTREGA));
        assertTrue(StatusPedido.EM_PREPARACAO.podeTransitarPara(StatusPedido.PRONTO, TipoEntrega.ENTREGA));
        assertTrue(StatusPedido.PRONTO.podeTransitarPara(StatusPedido.SAIU_PARA_ENTREGA, TipoEntrega.ENTREGA));
        assertTrue(StatusPedido.SAIU_PARA_ENTREGA.podeTransitarPara(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA));
    }

    @Test
    void fluxoDeRetiradaPermitido() {
        assertTrue(StatusPedido.PRONTO.podeTransitarPara(StatusPedido.RETIRADO, TipoEntrega.RETIRADA));
        assertTrue(StatusPedido.RETIRADO.podeTransitarPara(StatusPedido.FINALIZADO, TipoEntrega.RETIRADA));
    }

    @Test
    void entregaERetiradaNaoSeConfundem() {

        assertFalse(StatusPedido.PRONTO.podeTransitarPara(StatusPedido.RETIRADO, TipoEntrega.ENTREGA));
        assertFalse(StatusPedido.PRONTO.podeTransitarPara(StatusPedido.SAIU_PARA_ENTREGA, TipoEntrega.RETIRADA));
    }

    @Test
    void transicoesInvalidasBloqueadas() {
        assertFalse(StatusPedido.RECEBIDO.podeTransitarPara(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA));
        assertFalse(StatusPedido.EM_PREPARACAO.podeTransitarPara(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA));
        assertFalse(StatusPedido.PRONTO.podeTransitarPara(StatusPedido.RECEBIDO, TipoEntrega.ENTREGA));
    }

    @Test
    void estadosTerminais() {
        assertTrue(StatusPedido.FINALIZADO.terminal());
        assertTrue(StatusPedido.CANCELADO.terminal());
        assertTrue(StatusPedido.FINALIZADO.proximosPermitidos(TipoEntrega.ENTREGA).isEmpty());
        assertTrue(StatusPedido.CANCELADO.proximosPermitidos(TipoEntrega.RETIRADA).isEmpty());
    }

    @Test
    void anteriorAoDespacho() {
        assertTrue(StatusPedido.RECEBIDO.anteriorAoDespacho());
        assertTrue(StatusPedido.PRONTO.anteriorAoDespacho());
        assertFalse(StatusPedido.SAIU_PARA_ENTREGA.anteriorAoDespacho());
        assertFalse(StatusPedido.RETIRADO.anteriorAoDespacho());
        assertFalse(StatusPedido.FINALIZADO.anteriorAoDespacho());
    }

    @Test
    void despachado() {
        assertTrue(StatusPedido.SAIU_PARA_ENTREGA.despachado());
        assertTrue(StatusPedido.RETIRADO.despachado());
        assertFalse(StatusPedido.PRONTO.despachado());
    }
}
