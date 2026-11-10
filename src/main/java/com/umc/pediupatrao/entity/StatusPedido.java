package com.umc.pediupatrao.entity;

import java.util.EnumSet;
import java.util.Set;

public enum StatusPedido {

    RECEBIDO("Recebido"),
    EM_PREPARACAO("Em preparação"),
    PRONTO("Pronto"),
    SAIU_PARA_ENTREGA("Saiu para entrega"),
    RETIRADO("Retirado no balcão"),
    FINALIZADO("Finalizado"),
    CANCELADO("Cancelado");

    private final String descricao;

    StatusPedido(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    public Set<StatusPedido> proximosPermitidos(TipoEntrega tipo) {
        return switch (this) {
            case RECEBIDO -> EnumSet.of(EM_PREPARACAO);
            case EM_PREPARACAO -> EnumSet.of(PRONTO);
            case PRONTO -> {
                if (tipo == TipoEntrega.ENTREGA) {
                    yield EnumSet.of(SAIU_PARA_ENTREGA);
                }
                if (tipo == TipoEntrega.RETIRADA) {
                    yield EnumSet.of(RETIRADO);
                }
                yield EnumSet.of(SAIU_PARA_ENTREGA, RETIRADO);
            }
            case SAIU_PARA_ENTREGA, RETIRADO -> EnumSet.of(FINALIZADO);
            case FINALIZADO, CANCELADO -> EnumSet.noneOf(StatusPedido.class);
        };
    }

    public boolean podeTransitarPara(StatusPedido destino, TipoEntrega tipo) {
        return proximosPermitidos(tipo).contains(destino);
    }

    public boolean anteriorAoDespacho() {
        return this == RECEBIDO || this == EM_PREPARACAO || this == PRONTO;
    }

    public boolean despachado() {
        return this == SAIU_PARA_ENTREGA || this == RETIRADO;
    }

    public boolean terminal() {
        return this == FINALIZADO || this == CANCELADO;
    }
}
