package com.umc.pediupatrao.controller.api;

import com.umc.pediupatrao.dto.DashboardDtos.DashboardResumo;
import com.umc.pediupatrao.dto.DashboardDtos.FiltrosDisponiveis;
import com.umc.pediupatrao.dto.DashboardDtos.FormaPagamentoResumo;
import com.umc.pediupatrao.dto.DashboardDtos.IndicadoresVenda;
import com.umc.pediupatrao.dto.DashboardDtos.PontoPeriodo;
import com.umc.pediupatrao.dto.DashboardDtos.ProdutoVendido;
import com.umc.pediupatrao.dto.DashboardDtos.SituacaoStatus;
import com.umc.pediupatrao.entity.FormaPagamento;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.service.DashboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardApiController {

    private final DashboardService dashboardService;

    public DashboardApiController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/resumo")
    public DashboardResumo resumo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) StatusPedido status,
            @RequestParam(required = false) FormaPagamento formaPagamento,
            @RequestParam(defaultValue = "dia") String agrupamento,
            @RequestParam(defaultValue = "5") int limiteProdutos) {
        return dashboardService.resumo(inicio, fim, status, formaPagamento, agrupamento, limiteProdutos);
    }

    @GetMapping("/indicadores")
    public IndicadoresVenda indicadores(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) StatusPedido status,
            @RequestParam(required = false) FormaPagamento formaPagamento) {
        return dashboardService.indicadores(inicio, fim, status, formaPagamento);
    }

    @GetMapping("/vendas-periodo")
    public List<PontoPeriodo> vendasPorPeriodo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) StatusPedido status,
            @RequestParam(required = false) FormaPagamento formaPagamento,
            @RequestParam(defaultValue = "dia") String agrupamento) {
        return dashboardService.vendasPorPeriodo(inicio, fim, status, formaPagamento, agrupamento);
    }

    @GetMapping("/situacao-pedidos")
    public List<SituacaoStatus> situacaoPedidos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) FormaPagamento formaPagamento) {
        return dashboardService.situacaoPedidos(inicio, fim, formaPagamento);
    }

    @GetMapping("/produtos-mais-vendidos")
    public List<ProdutoVendido> produtosMaisVendidos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) StatusPedido status,
            @RequestParam(required = false) FormaPagamento formaPagamento,
            @RequestParam(defaultValue = "5") int limite) {
        return dashboardService.produtosMaisVendidos(inicio, fim, status, formaPagamento, limite);
    }

    @GetMapping("/formas-pagamento")
    public List<FormaPagamentoResumo> formasPagamento(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) StatusPedido status) {
        return dashboardService.formasPagamento(inicio, fim, status);
    }

    @GetMapping("/filtros")
    public FiltrosDisponiveis filtros() {
        return dashboardService.filtrosDisponiveis();
    }
}
