package com.umc.pediupatrao;

import com.umc.pediupatrao.entity.StatusPedido;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusPedidoTest {

    @Test
    void fluxoLinearPermitido() {
        assertTrue(StatusPedido.RECEBIDO.podeTransitarPara(StatusPedido.EM_PREPARACAO));
        assertTrue(StatusPedido.EM_PREPARACAO.podeTransitarPara(StatusPedido.PRONTO));
        assertTrue(StatusPedido.PRONTO.podeTransitarPara(StatusPedido.SAIU_PARA_ENTREGA_RETIRADO));
        assertTrue(StatusPedido.SAIU_PARA_ENTREGA_RETIRADO.podeTransitarPara(StatusPedido.FINALIZADO));
    }

    @Test
    void transicoesInvalidasBloqueadas() {
        assertFalse(StatusPedido.RECEBIDO.podeTransitarPara(StatusPedido.FINALIZADO));
        assertFalse(StatusPedido.EM_PREPARACAO.podeTransitarPara(StatusPedido.FINALIZADO));
        assertFalse(StatusPedido.PRONTO.podeTransitarPara(StatusPedido.RECEBIDO));
    }

    @Test
    void estadosTerminais() {
        assertTrue(StatusPedido.FINALIZADO.terminal());
        assertTrue(StatusPedido.CANCELADO.terminal());
        assertTrue(StatusPedido.FINALIZADO.proximosPermitidos().isEmpty());
        assertTrue(StatusPedido.CANCELADO.proximosPermitidos().isEmpty());
    }

    @Test
    void anteriorASaida() {
        assertTrue(StatusPedido.RECEBIDO.anteriorASaida());
        assertTrue(StatusPedido.PRONTO.anteriorASaida());
        assertFalse(StatusPedido.SAIU_PARA_ENTREGA_RETIRADO.anteriorASaida());
        assertFalse(StatusPedido.FINALIZADO.anteriorASaida());
    }
}
