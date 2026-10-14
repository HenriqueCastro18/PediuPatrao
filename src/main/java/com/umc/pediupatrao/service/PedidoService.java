package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.CampoAlterado;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.FormaPagamento;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.security.SecurityUtils;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PedidoService {

    private static final String ENTIDADE = "PEDIDO";
    private static final double LIMITE_DESCONTO = 20.0;

    private final PedidoRepository pedidoRepository;
    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;
    private final AuditoriaService auditoriaService;

    public PedidoService(PedidoRepository pedidoRepository,
                         ClienteRepository clienteRepository,
                         ProdutoRepository produtoRepository,
                         AuditoriaService auditoriaService) {
        this.pedidoRepository = pedidoRepository;
        this.clienteRepository = clienteRepository;
        this.produtoRepository = produtoRepository;
        this.auditoriaService = auditoriaService;
    }

    public List<Pedido> listarPedidos() {
        return pedidoRepository.findAll(Sort.by(Sort.Direction.DESC, "dataHoraEntrada"));
    }

    public Pedido buscarPorId(String id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado"));
    }

    @PreAuthorize("hasAnyRole('ATENDENTE', 'GERENTE')")
    public Pedido criar(String clienteId, List<String> produtoIds, List<Integer> quantidades,
                        String formaPagamento, Double valorRecebido) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente informado não existe"));
        FormaPagamento forma = interpretarFormaPagamento(formaPagamento);

        List<ItemPedido> itens = montarItens(produtoIds, quantidades);
        if (itens.isEmpty()) {
            throw new IllegalArgumentException("O pedido deve conter ao menos um item");
        }
        validarEstoque(itens);

        Pedido pedido = new Pedido();
        pedido.setClienteId(cliente.getId());
        pedido.setClienteNome(cliente.getNome());
        pedido.setItens(itens);
        pedido.setPercentualDesconto(0.0);
        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setFormaPagamento(forma);
        pedido.setValorRecebido(forma == FormaPagamento.DINHEIRO ? valorRecebidoInformado(valorRecebido) : 0.0);
        pedido.setUsuarioRegistro(SecurityUtils.usuarioAtual());
        pedido.setDataHoraEntrada(LocalDateTime.now());
        recalcular(pedido);

        if (forma == FormaPagamento.DINHEIRO && pedido.getValorRecebido() < pedido.getValorTotal()) {
            throw new IllegalArgumentException("O valor recebido é menor que o total do pedido (R$ "
                    + formatar(pedido.getValorTotal()) + ")");
        }

        Pedido salvo = pedidoRepository.save(pedido);
        movimentarEstoque(itens, -1);

        List<CampoAlterado> campos = new ArrayList<>();
        campos.add(new CampoAlterado("cliente", null, salvo.getClienteNome()));
        campos.add(new CampoAlterado("itens", null, String.valueOf(salvo.getItens().size())));
        campos.add(new CampoAlterado("valorTotal", null, formatar(salvo.getValorTotal())));
        campos.add(new CampoAlterado("formaPagamento", null, forma.name()));
        campos.add(new CampoAlterado("status", null, salvo.getStatus().name()));
        auditoriaService.registrar("INCLUSAO", ENTIDADE, salvo.getId(), campos);

        return salvo;
    }

    @PreAuthorize("hasAnyRole('ATENDENTE', 'GERENTE')")
    public Pedido avancarStatus(String id, StatusPedido destino) {
        Pedido pedido = buscarPorId(id);
        StatusPedido atual = pedido.getStatus();

        if (atual.terminal()) {
            throw new IllegalStateException("Pedido " + atual.getDescricao().toLowerCase()
                    + " não permite alteração de status");
        }
        if (!atual.podeTransitarPara(destino)) {
            throw new IllegalStateException("Transição inválida: de " + atual.getDescricao()
                    + " para " + destino.getDescricao());
        }

        pedido.setStatus(destino);
        if (destino == StatusPedido.SAIU_PARA_ENTREGA_RETIRADO) {
            pedido.setUsuarioSaida(SecurityUtils.usuarioAtual());
            pedido.setDataHoraSaida(LocalDateTime.now());
        }

        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar("MUDANCA_STATUS", ENTIDADE, salvo.getId(),
                List.of(new CampoAlterado("status", atual.name(), destino.name())));
        return salvo;
    }

    @PreAuthorize("hasRole('GERENTE')")
    public Pedido aplicarDesconto(String id, double percentual) {
        Pedido pedido = buscarPorId(id);

        if (percentual < 0) {
            throw new IllegalArgumentException("Percentual de desconto inválido");
        }
        if (percentual > LIMITE_DESCONTO) {
            throw new IllegalStateException("O desconto não pode ultrapassar 20%");
        }
        if (!pedido.getStatus().anteriorASaida()) {
            throw new IllegalStateException("O desconto só pode ser aplicado antes da saída para entrega ou retirada");
        }

        double totalAnterior = pedido.getValorTotal();
        double percentualAnterior = pedido.getPercentualDesconto();
        pedido.setPercentualDesconto(percentual);
        recalcular(pedido);

        Pedido salvo = pedidoRepository.save(pedido);

        List<CampoAlterado> campos = new ArrayList<>();
        campos.add(new CampoAlterado("percentualDesconto", formatar(percentualAnterior), formatar(percentual)));
        campos.add(new CampoAlterado("valorTotal", formatar(totalAnterior), formatar(salvo.getValorTotal())));
        auditoriaService.registrar("DESCONTO", ENTIDADE, salvo.getId(), campos);

        return salvo;
    }

    @PreAuthorize("hasRole('GERENTE')")
    public Pedido cancelar(String id, String justificativa) {
        Pedido pedido = buscarPorId(id);

        if (pedido.getStatus() == StatusPedido.CANCELADO) {
            throw new IllegalStateException("Pedido já cancelado");
        }
        if (!pedido.getStatus().anteriorASaida()) {
            throw new IllegalStateException("Não é possível cancelar um pedido que já saiu para entrega ou retirada");
        }
        if (justificativa == null || justificativa.isBlank()) {
            throw new IllegalArgumentException("A justificativa do cancelamento é obrigatória");
        }

        StatusPedido atual = pedido.getStatus();
        pedido.setStatus(StatusPedido.CANCELADO);
        pedido.setUsuarioCancelamento(SecurityUtils.usuarioAtual());
        pedido.setDataHoraCancelamento(LocalDateTime.now());
        pedido.setJustificativaCancelamento(justificativa.trim());

        Pedido salvo = pedidoRepository.save(pedido);
        movimentarEstoque(salvo.getItens(), 1);
        auditoriaService.registrar("CANCELAMENTO", ENTIDADE, salvo.getId(),
                List.of(new CampoAlterado("status", atual.name(), StatusPedido.CANCELADO.name())),
                justificativa.trim());
        return salvo;
    }

    private FormaPagamento interpretarFormaPagamento(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Selecione a forma de pagamento");
        }
        try {
            return FormaPagamento.valueOf(valor);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Forma de pagamento inválida");
        }
    }

    private double valorRecebidoInformado(Double valorRecebido) {
        if (valorRecebido == null) {
            throw new IllegalArgumentException("Informe o valor recebido em dinheiro");
        }
        if (valorRecebido < 0) {
            throw new IllegalArgumentException("O valor recebido não pode ser negativo");
        }
        return arredondar(valorRecebido);
    }

    private List<ItemPedido> montarItens(List<String> produtoIds, List<Integer> quantidades) {
        List<ItemPedido> itens = new ArrayList<>();
        if (produtoIds == null || quantidades == null) {
            return itens;
        }
        for (int i = 0; i < produtoIds.size() && i < quantidades.size(); i++) {
            String produtoId = produtoIds.get(i);
            Integer qtd = quantidades.get(i);
            if (produtoId == null || produtoId.isBlank() || qtd == null || qtd <= 0) {
                continue;
            }
            Produto produto = produtoRepository.findById(produtoId)
                    .orElseThrow(() -> new IllegalArgumentException("Produto informado não existe"));
            double preco = produto.getPreco() != null ? produto.getPreco() : 0.0;
            itens.add(new ItemPedido(produto.getId(), produto.getNome(), qtd, preco));
        }
        return itens;
    }

    private void validarEstoque(List<ItemPedido> itens) {
        for (Map.Entry<String, Integer> entrada : agruparQuantidades(itens).entrySet()) {
            Produto produto = produtoRepository.findById(entrada.getKey())
                    .orElseThrow(() -> new IllegalArgumentException("Produto informado não existe"));
            int disponivel = estoqueAtual(produto);
            if (entrada.getValue() > disponivel) {
                throw new IllegalStateException("Estoque insuficiente de " + produto.getNome()
                        + " (disponível: " + disponivel + ")");
            }
        }
    }

    private void movimentarEstoque(List<ItemPedido> itens, int sinal) {
        for (Map.Entry<String, Integer> entrada : agruparQuantidades(itens).entrySet()) {
            produtoRepository.findById(entrada.getKey()).ifPresent(produto -> {
                produto.setQuantidadeEstoque(estoqueAtual(produto) + sinal * entrada.getValue());
                produtoRepository.save(produto);
            });
        }
    }

    private Map<String, Integer> agruparQuantidades(List<ItemPedido> itens) {
        Map<String, Integer> totais = new LinkedHashMap<>();
        for (ItemPedido item : itens) {
            totais.merge(item.getProdutoId(), item.getQuantidade(), Integer::sum);
        }
        return totais;
    }

    private int estoqueAtual(Produto produto) {
        return produto.getQuantidadeEstoque() != null ? produto.getQuantidadeEstoque() : 0;
    }

    private void recalcular(Pedido pedido) {
        double bruto = 0.0;
        for (ItemPedido item : pedido.getItens()) {
            bruto += item.getSubtotal();
        }
        bruto = arredondar(bruto);
        double desconto = arredondar(bruto * pedido.getPercentualDesconto() / 100.0);

        pedido.setValorBruto(bruto);
        pedido.setValorDesconto(desconto);
        pedido.setValorTotal(arredondar(bruto - desconto));
        aplicarPagamento(pedido);
    }

    private void aplicarPagamento(Pedido pedido) {
        if (pedido.getFormaPagamento() == FormaPagamento.DINHEIRO) {
            pedido.setTroco(arredondar(Math.max(0, pedido.getValorRecebido() - pedido.getValorTotal())));
        } else {
            pedido.setTroco(0.0);
        }
    }

    private double arredondar(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }

    private String formatar(double valor) {
        return String.format(Locale.US, "%.2f", valor);
    }
}
