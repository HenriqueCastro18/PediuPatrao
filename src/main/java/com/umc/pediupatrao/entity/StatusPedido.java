package com.umc.pediupatrao.entity;

import java.util.EnumSet;
import java.util.Set;

public enum StatusPedido {

    RECEBIDO("Recebido"),
    EM_PREPARACAO("Em preparação"),
    PRONTO("Pronto"),
    SAIU_PARA_ENTREGA_RETIRADO("Saiu para entrega / Retirado"),
    FINALIZADO("Finalizado"),
    CANCELADO("Cancelado");

    private final String descricao;

    StatusPedido(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    public Set<StatusPedido> proximosPermitidos() {
        return switch (this) {
            case RECEBIDO -> EnumSet.of(EM_PREPARACAO);
            case EM_PREPARACAO -> EnumSet.of(PRONTO);
            case PRONTO -> EnumSet.of(SAIU_PARA_ENTREGA_RETIRADO);
            case SAIU_PARA_ENTREGA_RETIRADO -> EnumSet.of(FINALIZADO);
            case FINALIZADO, CANCELADO -> EnumSet.noneOf(StatusPedido.class);
        };
    }

    public boolean podeTransitarPara(StatusPedido destino) {
        return proximosPermitidos().contains(destino);
    }

    public boolean anteriorASaida() {
        return this == RECEBIDO || this == EM_PREPARACAO || this == PRONTO;
    }

    public boolean terminal() {
        return this == FINALIZADO || this == CANCELADO;
    }
}
