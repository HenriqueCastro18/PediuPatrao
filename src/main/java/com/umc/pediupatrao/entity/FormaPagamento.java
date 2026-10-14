package com.umc.pediupatrao.entity;

public enum FormaPagamento {

    DINHEIRO("Dinheiro"),
    PIX("Pix"),
    CARTAO_DEBITO("Cartão de débito"),
    CARTAO_CREDITO("Cartão de crédito");

    private final String descricao;

    FormaPagamento(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
