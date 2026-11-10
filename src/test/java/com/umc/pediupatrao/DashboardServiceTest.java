package com.umc.pediupatrao;

import com.umc.pediupatrao.dto.DashboardDtos.DashboardResumo;
import com.umc.pediupatrao.dto.DashboardDtos.IndicadoresVenda;
import com.umc.pediupatrao.dto.DashboardDtos.ProdutoVendido;
import com.umc.pediupatrao.dto.DashboardDtos.SituacaoStatus;
import com.umc.pediupatrao.entity.FormaPagamento;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;

    @InjectMocks
    private DashboardService dashboardService;

    private Pedido pedido(StatusPedido status, FormaPagamento forma, double total, double desconto,
                          LocalDateTime data, ItemPedido... itens) {
        Pedido p = new Pedido();
        p.setStatus(status);
        p.setFormaPagamento(forma);
        p.setValorTotal(total);
        p.setValorDesconto(desconto);
        p.setDataHoraEntrada(data);
        for (ItemPedido i : itens) {
            p.getItens().add(i);
        }
        return p;
    }

    private void comPedidos(List<Pedido> pedidos) {
        when(pedidoRepository.findByDataHoraEntradaBetween(any(), any())).thenReturn(pedidos);
    }

    @Test
    void indicadoresIgnoramCancelados() {
        comPedidos(List.of(
                pedido(StatusPedido.FINALIZADO, FormaPagamento.PIX, 100.0, 10.0, LocalDateTime.now(),
                        new ItemPedido("p1", "Pizza", 2, 50.0)),
                pedido(StatusPedido.RECEBIDO, FormaPagamento.DINHEIRO, 60.0, 0.0, LocalDateTime.now(),
                        new ItemPedido("p2", "Suco", 3, 20.0)),
                pedido(StatusPedido.CANCELADO, FormaPagamento.PIX, 999.0, 0.0, LocalDateTime.now(),
                        new ItemPedido("p1", "Pizza", 5, 50.0))));

        IndicadoresVenda ind = dashboardService.indicadores(null, null, null, null);

        assertEquals(3, ind.totalPedidos());
        assertEquals(2, ind.pedidosValidos());
        assertEquals(1, ind.pedidosCancelados());
        assertEquals(160.0, ind.faturamentoTotal());
        assertEquals(80.0, ind.ticketMedio());
        assertEquals(5, ind.itensVendidos());
        assertEquals(10.0, ind.descontoConcedido());
    }

    @Test
    void ticketMedioZeroSemPedidosValidos() {
        comPedidos(List.of(
                pedido(StatusPedido.CANCELADO, FormaPagamento.PIX, 50.0, 0.0, LocalDateTime.now())));
        IndicadoresVenda ind = dashboardService.indicadores(null, null, null, null);
        assertEquals(0.0, ind.faturamentoTotal());
        assertEquals(0.0, ind.ticketMedio());
    }

    @Test
    void topProdutosOrdenadosPorQuantidade() {
        comPedidos(List.of(
                pedido(StatusPedido.FINALIZADO, FormaPagamento.PIX, 0.0, 0.0, LocalDateTime.now(),
                        new ItemPedido("p1", "Pizza", 2, 50.0), new ItemPedido("p2", "Suco", 1, 10.0)),
                pedido(StatusPedido.RECEBIDO, FormaPagamento.PIX, 0.0, 0.0, LocalDateTime.now(),
                        new ItemPedido("p2", "Suco", 5, 10.0)),
                pedido(StatusPedido.CANCELADO, FormaPagamento.PIX, 0.0, 0.0, LocalDateTime.now(),
                        new ItemPedido("p1", "Pizza", 100, 50.0))));

        List<ProdutoVendido> top = dashboardService.produtosMaisVendidos(null, null, null, null, 5);

        assertEquals(2, top.size());
        assertEquals("Suco", top.get(0).nome());
        assertEquals(6, top.get(0).quantidade());
        assertEquals("Pizza", top.get(1).nome());
        assertEquals(2, top.get(1).quantidade());
    }

    @Test
    void situacaoListaTodosOsStatus() {
        comPedidos(List.of(
                pedido(StatusPedido.RECEBIDO, FormaPagamento.PIX, 40.0, 0.0, LocalDateTime.now()),
                pedido(StatusPedido.RECEBIDO, FormaPagamento.PIX, 60.0, 0.0, LocalDateTime.now()),
                pedido(StatusPedido.CANCELADO, FormaPagamento.PIX, 30.0, 0.0, LocalDateTime.now())));

        List<SituacaoStatus> situacao = dashboardService.situacaoPedidos(null, null, null);

        assertEquals(StatusPedido.values().length, situacao.size());
        SituacaoStatus recebido = situacao.stream().filter(s -> s.status().equals("RECEBIDO")).findFirst().orElseThrow();
        assertEquals(2, recebido.quantidade());
        assertEquals(100.0, recebido.valorTotal());
    }

    @Test
    void vendasPorPeriodoAgrupaPorDiaEIgnoraCancelados() {
        LocalDateTime d1 = LocalDateTime.of(2026, 1, 10, 12, 0);
        LocalDateTime d2 = LocalDateTime.of(2026, 1, 11, 9, 0);
        comPedidos(List.of(
                pedido(StatusPedido.FINALIZADO, FormaPagamento.PIX, 100.0, 0.0, d1),
                pedido(StatusPedido.RECEBIDO, FormaPagamento.PIX, 50.0, 0.0, d1),
                pedido(StatusPedido.FINALIZADO, FormaPagamento.PIX, 80.0, 0.0, d2),
                pedido(StatusPedido.CANCELADO, FormaPagamento.PIX, 999.0, 0.0, d2)));

        var serie = dashboardService.vendasPorPeriodo(null, null, null, null, "dia");

        assertEquals(2, serie.size());
        assertEquals("2026-01-10", serie.get(0).rotulo());
        assertEquals(150.0, serie.get(0).faturamento());
        assertEquals(2, serie.get(0).pedidos());
        assertEquals("2026-01-11", serie.get(1).rotulo());
        assertEquals(80.0, serie.get(1).faturamento());
    }

    @Test
    void filtroPorFormaPagamento() {
        comPedidos(List.of(
                pedido(StatusPedido.FINALIZADO, FormaPagamento.PIX, 100.0, 0.0, LocalDateTime.now()),
                pedido(StatusPedido.FINALIZADO, FormaPagamento.DINHEIRO, 70.0, 0.0, LocalDateTime.now())));

        IndicadoresVenda ind = dashboardService.indicadores(null, null, null, FormaPagamento.PIX);
        assertEquals(1, ind.pedidosValidos());
        assertEquals(100.0, ind.faturamentoTotal());
    }

    @Test
    void periodoInvalidoLancaErro() {
        assertThrows(IllegalArgumentException.class,
                () -> dashboardService.indicadores(LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 1), null, null));
    }

    @Test
    void resumoConsolidaTudo() {
        comPedidos(List.of(
                pedido(StatusPedido.FINALIZADO, FormaPagamento.PIX, 100.0, 0.0, LocalDateTime.now(),
                        new ItemPedido("p1", "Pizza", 1, 100.0))));

        DashboardResumo resumo = dashboardService.resumo(null, null, null, null, "dia", 5);

        assertEquals(1, resumo.indicadores().pedidosValidos());
        assertTrue(resumo.produtosMaisVendidos().size() >= 1);
        assertEquals(StatusPedido.values().length, resumo.situacaoPedidos().size());
        assertEquals(1, resumo.formasPagamento().size());
    }
}
