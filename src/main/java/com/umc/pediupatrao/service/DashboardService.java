package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.DashboardDtos.DashboardResumo;
import com.umc.pediupatrao.dto.DashboardDtos.FiltrosDisponiveis;
import com.umc.pediupatrao.dto.DashboardDtos.FormaPagamentoResumo;
import com.umc.pediupatrao.dto.DashboardDtos.IndicadoresVenda;
import com.umc.pediupatrao.dto.DashboardDtos.OpcaoFiltro;
import com.umc.pediupatrao.dto.DashboardDtos.PontoPeriodo;
import com.umc.pediupatrao.dto.DashboardDtos.ProdutoVendido;
import com.umc.pediupatrao.dto.DashboardDtos.SituacaoStatus;
import com.umc.pediupatrao.entity.FormaPagamento;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.repository.PedidoRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
public class DashboardService {

    private static final DateTimeFormatter FMT_DIA = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FMT_MES = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final LocalDate DATA_MINIMA = LocalDate.of(2000, 1, 1);

    private final PedidoRepository pedidoRepository;

    public DashboardService(PedidoRepository pedidoRepository) {
        this.pedidoRepository = pedidoRepository;
    }

    public DashboardResumo resumo(LocalDate inicio, LocalDate fim, StatusPedido status,
                                  FormaPagamento forma, String agrupamento, int limiteProdutos) {
        Periodo periodo = normalizarPeriodo(inicio, fim);
        List<Pedido> filtrados = filtrar(periodo, status, forma);
        return new DashboardResumo(
                calcularIndicadores(filtrados, periodo),
                calcularVendasPorPeriodo(filtrados, agrupamento),
                calcularSituacao(filtrados),
                calcularTopProdutos(filtrados, limiteProdutos),
                calcularFormasPagamento(filtrados));
    }

    public IndicadoresVenda indicadores(LocalDate inicio, LocalDate fim, StatusPedido status, FormaPagamento forma) {
        Periodo periodo = normalizarPeriodo(inicio, fim);
        return calcularIndicadores(filtrar(periodo, status, forma), periodo);
    }

    public List<PontoPeriodo> vendasPorPeriodo(LocalDate inicio, LocalDate fim, StatusPedido status,
                                               FormaPagamento forma, String agrupamento) {
        Periodo periodo = normalizarPeriodo(inicio, fim);
        return calcularVendasPorPeriodo(filtrar(periodo, status, forma), agrupamento);
    }

    public List<SituacaoStatus> situacaoPedidos(LocalDate inicio, LocalDate fim, FormaPagamento forma) {
        Periodo periodo = normalizarPeriodo(inicio, fim);
        return calcularSituacao(filtrar(periodo, null, forma));
    }

    public List<ProdutoVendido> produtosMaisVendidos(LocalDate inicio, LocalDate fim, StatusPedido status,
                                                     FormaPagamento forma, int limite) {
        Periodo periodo = normalizarPeriodo(inicio, fim);
        return calcularTopProdutos(filtrar(periodo, status, forma), limite);
    }

    public List<FormaPagamentoResumo> formasPagamento(LocalDate inicio, LocalDate fim, StatusPedido status) {
        Periodo periodo = normalizarPeriodo(inicio, fim);
        return calcularFormasPagamento(filtrar(periodo, status, null));
    }

    public FiltrosDisponiveis filtrosDisponiveis() {
        List<OpcaoFiltro> status = new ArrayList<>();
        for (StatusPedido s : StatusPedido.values()) {
            status.add(new OpcaoFiltro(s.name(), s.getDescricao()));
        }
        List<OpcaoFiltro> formas = new ArrayList<>();
        for (FormaPagamento f : FormaPagamento.values()) {
            formas.add(new OpcaoFiltro(f.name(), f.getDescricao()));
        }
        List<OpcaoFiltro> tipos = new ArrayList<>();
        for (TipoEntrega t : TipoEntrega.values()) {
            tipos.add(new OpcaoFiltro(t.name(), t.getDescricao()));
        }
        return new FiltrosDisponiveis(status, formas, tipos);
    }

    private List<Pedido> filtrar(Periodo periodo, StatusPedido status, FormaPagamento forma) {
        List<Pedido> pedidos = pedidoRepository.findByDataHoraEntradaBetween(periodo.inicio(), periodo.fim());
        List<Pedido> resultado = new ArrayList<>();
        for (Pedido p : pedidos) {
            if (status != null && p.getStatus() != status) {
                continue;
            }
            if (forma != null && p.getFormaPagamento() != forma) {
                continue;
            }
            resultado.add(p);
        }
        return resultado;
    }

    private IndicadoresVenda calcularIndicadores(List<Pedido> pedidos, Periodo periodo) {
        long total = pedidos.size();
        long cancelados = 0;
        long validos = 0;
        double faturamento = 0.0;
        double desconto = 0.0;
        long itens = 0;

        for (Pedido p : pedidos) {
            if (p.getStatus() == StatusPedido.CANCELADO) {
                cancelados++;
                continue;
            }
            validos++;
            faturamento += p.getValorTotal();
            desconto += p.getValorDesconto();
            for (ItemPedido item : p.getItens()) {
                itens += item.getQuantidade();
            }
        }

        double ticket = validos > 0 ? faturamento / validos : 0.0;
        return new IndicadoresVenda(
                total, validos, cancelados,
                arredondar(faturamento), arredondar(ticket), itens, arredondar(desconto),
                periodo.inicioData().format(FMT_DIA), periodo.fimData().format(FMT_DIA));
    }

    private List<PontoPeriodo> calcularVendasPorPeriodo(List<Pedido> pedidos, String agrupamento) {
        boolean porMes = "mes".equalsIgnoreCase(agrupamento);
        DateTimeFormatter fmt = porMes ? FMT_MES : FMT_DIA;

        Map<String, double[]> buckets = new LinkedHashMap<>();
        for (Pedido p : pedidos) {
            if (p.getStatus() == StatusPedido.CANCELADO || p.getDataHoraEntrada() == null) {
                continue;
            }
            String chave = p.getDataHoraEntrada().format(fmt);
            double[] acc = buckets.computeIfAbsent(chave, k -> new double[]{0.0, 0.0});
            acc[0] += p.getValorTotal();
            acc[1] += 1;
        }

        return buckets.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new PontoPeriodo(e.getKey(), arredondar(e.getValue()[0]), (long) e.getValue()[1]))
                .toList();
    }

    private List<SituacaoStatus> calcularSituacao(List<Pedido> pedidos) {
        Map<StatusPedido, long[]> contagem = new LinkedHashMap<>();
        Map<StatusPedido, Double> valores = new LinkedHashMap<>();
        for (StatusPedido s : StatusPedido.values()) {
            contagem.put(s, new long[]{0});
            valores.put(s, 0.0);
        }
        for (Pedido p : pedidos) {
            StatusPedido s = p.getStatus();
            if (s == null) {
                continue;
            }
            contagem.get(s)[0]++;
            valores.merge(s, p.getValorTotal(), Double::sum);
        }

        List<SituacaoStatus> resultado = new ArrayList<>();
        for (StatusPedido s : StatusPedido.values()) {
            resultado.add(new SituacaoStatus(
                    s.name(), s.getDescricao(), contagem.get(s)[0], arredondar(valores.get(s))));
        }
        return resultado;
    }

    private List<ProdutoVendido> calcularTopProdutos(List<Pedido> pedidos, int limite) {
        int efetivo = limite <= 0 ? 5 : Math.min(limite, 50);
        Map<String, long[]> quantidades = new LinkedHashMap<>();
        Map<String, Double> faturamentos = new LinkedHashMap<>();
        Map<String, String> nomes = new LinkedHashMap<>();

        for (Pedido p : pedidos) {
            if (p.getStatus() == StatusPedido.CANCELADO) {
                continue;
            }
            for (ItemPedido item : p.getItens()) {
                String id = item.getProdutoId() != null ? item.getProdutoId() : item.getDescricao();
                quantidades.computeIfAbsent(id, k -> new long[]{0})[0] += item.getQuantidade();
                faturamentos.merge(id, item.getSubtotal(), Double::sum);
                nomes.putIfAbsent(id, item.getDescricao());
            }
        }

        return quantidades.entrySet().stream()
                .map(e -> new ProdutoVendido(
                        e.getKey(), nomes.get(e.getKey()), e.getValue()[0], arredondar(faturamentos.get(e.getKey()))))
                .sorted(Comparator.comparingLong(ProdutoVendido::quantidade).reversed()
                        .thenComparing(Comparator.comparingDouble(ProdutoVendido::faturamento).reversed()))
                .limit(efetivo)
                .toList();
    }

    private List<FormaPagamentoResumo> calcularFormasPagamento(List<Pedido> pedidos) {
        Map<FormaPagamento, long[]> contagem = new LinkedHashMap<>();
        Map<FormaPagamento, Double> valores = new LinkedHashMap<>();
        for (Pedido p : pedidos) {
            if (p.getStatus() == StatusPedido.CANCELADO || p.getFormaPagamento() == null) {
                continue;
            }
            FormaPagamento f = p.getFormaPagamento();
            contagem.computeIfAbsent(f, k -> new long[]{0})[0]++;
            valores.merge(f, p.getValorTotal(), Double::sum);
        }

        List<FormaPagamentoResumo> resultado = new ArrayList<>();
        for (FormaPagamento f : FormaPagamento.values()) {
            if (contagem.containsKey(f)) {
                resultado.add(new FormaPagamentoResumo(
                        f.name(), f.getDescricao(), contagem.get(f)[0], arredondar(valores.get(f))));
            }
        }
        return resultado;
    }

    private Periodo normalizarPeriodo(LocalDate inicio, LocalDate fim) {
        LocalDate ini = inicio != null ? inicio : DATA_MINIMA;
        LocalDate f = fim != null ? fim : LocalDate.now();
        if (ini.isAfter(f)) {
            throw new IllegalArgumentException("A data inicial não pode ser posterior à data final.");
        }
        return new Periodo(ini, f, ini.atStartOfDay(), f.atTime(23, 59, 59));
    }

    private double arredondar(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }

    private record Periodo(LocalDate inicioData, LocalDate fimData, LocalDateTime inicio, LocalDateTime fim) {
    }
}
