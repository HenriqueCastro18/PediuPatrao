package com.umc.pediupatrao.dto;

import java.util.List;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record IndicadoresVenda(
            long totalPedidos,
            long pedidosValidos,
            long pedidosCancelados,
            double faturamentoTotal,
            double ticketMedio,
            long itensVendidos,
            double descontoConcedido,
            String periodoInicio,
            String periodoFim) {
    }

    public record PontoPeriodo(
            String rotulo,
            double faturamento,
            long pedidos) {
    }

    public record SituacaoStatus(
            String status,
            String descricao,
            long quantidade,
            double valorTotal) {
    }

    public record ProdutoVendido(
            String produtoId,
            String nome,
            long quantidade,
            double faturamento) {
    }

    public record FormaPagamentoResumo(
            String forma,
            String descricao,
            long quantidade,
            double valor) {
    }

    public record OpcaoFiltro(
            String valor,
            String descricao) {
    }

    public record FiltrosDisponiveis(
            List<OpcaoFiltro> status,
            List<OpcaoFiltro> formasPagamento,
            List<OpcaoFiltro> tiposEntrega) {
    }

    public record DashboardResumo(
            IndicadoresVenda indicadores,
            List<PontoPeriodo> vendasPorPeriodo,
            List<SituacaoStatus> situacaoPedidos,
            List<ProdutoVendido> produtosMaisVendidos,
            List<FormaPagamentoResumo> formasPagamento) {
    }
}
