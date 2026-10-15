package com.umc.pediupatrao;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.FormaPagamento;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.PedidoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;
    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private ProdutoRepository produtoRepository;
    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private PedidoService pedidoService;

    @BeforeEach
    void setup() {
        lenient().when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> {
            Pedido p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId("gerado-1");
            }
            return p;
        });
    }

    private Produto pizza(int estoque) {
        Produto produto = new Produto();
        produto.setId("p1");
        produto.setNome("Pizza");
        produto.setPreco(50.0);
        produto.setQuantidadeEstoque(estoque);
        return produto;
    }

    private Pedido pedidoEm(StatusPedido status) {
        Pedido pedido = new Pedido();
        pedido.setId("x1");
        pedido.getItens().add(new ItemPedido("p1", "Pizza", 2, 50.0));
        pedido.setPercentualDesconto(0.0);
        pedido.setValorBruto(100.0);
        pedido.setValorTotal(100.0);
        pedido.setStatus(status);
        return pedido;
    }

    private void clienteExistente() {
        Cliente cliente = new Cliente();
        cliente.setId("c1");
        cliente.setNome("Cliente Teste");
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente));
    }

    @Test
    void criarCalculaTotalNoServidorEBaixaEstoque() {
        clienteExistente();
        Produto pizza = pizza(10);
        when(produtoRepository.findById("p1")).thenReturn(Optional.of(pizza));

        Pedido pedido = pedidoService.criar("c1", List.of("p1"), List.of(3), "PIX", null);

        assertEquals(150.0, pedido.getValorTotal());
        assertEquals(StatusPedido.RECEBIDO, pedido.getStatus());
        assertEquals(FormaPagamento.PIX, pedido.getFormaPagamento());
        assertEquals(7, pizza.getQuantidadeEstoque());
        verify(auditoriaService).registrar(any(), any(), any(), any());
    }

    @Test
    void dinheiroCalculaTroco() {
        clienteExistente();
        when(produtoRepository.findById("p1")).thenReturn(Optional.of(pizza(10)));

        Pedido pedido = pedidoService.criar("c1", List.of("p1"), List.of(3), "DINHEIRO", 200.0);

        assertEquals(200.0, pedido.getValorRecebido());
        assertEquals(50.0, pedido.getTroco());
    }

    @Test
    void dinheiroComValorInsuficienteBloqueado() {
        clienteExistente();
        when(produtoRepository.findById("p1")).thenReturn(Optional.of(pizza(10)));

        assertThrows(IllegalArgumentException.class,
                () -> pedidoService.criar("c1", List.of("p1"), List.of(3), "DINHEIRO", 100.0));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void dinheiroSemValorRecebidoBloqueado() {
        clienteExistente();
        when(produtoRepository.findById("p1")).thenReturn(Optional.of(pizza(10)));

        assertThrows(IllegalArgumentException.class,
                () -> pedidoService.criar("c1", List.of("p1"), List.of(3), "DINHEIRO", null));
    }

    @Test
    void formaPagamentoObrigatoria() {
        clienteExistente();
        assertThrows(IllegalArgumentException.class,
                () -> pedidoService.criar("c1", List.of("p1"), List.of(3), "", null));
    }

    @Test
    void estoqueInsuficienteBloqueiaPedido() {
        clienteExistente();
        when(produtoRepository.findById("p1")).thenReturn(Optional.of(pizza(2)));

        assertThrows(IllegalStateException.class,
                () -> pedidoService.criar("c1", List.of("p1"), List.of(3), "PIX", null));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void cancelamentoDevolveEstoque() {
        Produto pizza = pizza(5);
        when(pedidoRepository.findById("x1")).thenReturn(Optional.of(pedidoEm(StatusPedido.RECEBIDO)));
        when(produtoRepository.findById("p1")).thenReturn(Optional.of(pizza));

        pedidoService.cancelar("x1", "Cliente desistiu");

        assertEquals(7, pizza.getQuantidadeEstoque());
    }

    @Test
    void descontoValidoRecalcula() {
        when(pedidoRepository.findById("x1")).thenReturn(Optional.of(pedidoEm(StatusPedido.RECEBIDO)));
        Pedido pedido = pedidoService.aplicarDesconto("x1", 10.0);
        assertEquals(90.0, pedido.getValorTotal());
        assertEquals(10.0, pedido.getValorDesconto());
    }

    @Test
    void descontoAcimaDoLimiteBloqueado() {
        when(pedidoRepository.findById("x1")).thenReturn(Optional.of(pedidoEm(StatusPedido.RECEBIDO)));
        assertThrows(IllegalStateException.class, () -> pedidoService.aplicarDesconto("x1", 25.0));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void descontoAposSaidaBloqueado() {
        when(pedidoRepository.findById("x1")).thenReturn(Optional.of(pedidoEm(StatusPedido.SAIU_PARA_ENTREGA_RETIRADO)));
        assertThrows(IllegalStateException.class, () -> pedidoService.aplicarDesconto("x1", 10.0));
    }

    @Test
    void transicaoInvalidaBloqueada() {
        when(pedidoRepository.findById("x1")).thenReturn(Optional.of(pedidoEm(StatusPedido.RECEBIDO)));
        assertThrows(IllegalStateException.class, () -> pedidoService.avancarStatus("x1", StatusPedido.FINALIZADO));
    }

    @Test
    void saidaRegistraResponsavel() {
        when(pedidoRepository.findById("x1")).thenReturn(Optional.of(pedidoEm(StatusPedido.PRONTO)));
        Pedido pedido = pedidoService.avancarStatus("x1", StatusPedido.SAIU_PARA_ENTREGA_RETIRADO);
        assertEquals(StatusPedido.SAIU_PARA_ENTREGA_RETIRADO, pedido.getStatus());
        assertEquals("sistema", pedido.getUsuarioSaida());
    }

    @Test
    void cancelamentoAposSaidaBloqueado() {
        when(pedidoRepository.findById("x1")).thenReturn(Optional.of(pedidoEm(StatusPedido.SAIU_PARA_ENTREGA_RETIRADO)));
        assertThrows(IllegalStateException.class, () -> pedidoService.cancelar("x1", "qualquer"));
    }

    @Test
    void cancelamentoSemJustificativaBloqueado() {
        when(pedidoRepository.findById("x1")).thenReturn(Optional.of(pedidoEm(StatusPedido.RECEBIDO)));
        assertThrows(IllegalArgumentException.class, () -> pedidoService.cancelar("x1", "  "));
    }

    @Test
    void cancelamentoCongelaPedido() {
        when(pedidoRepository.findById("x1")).thenReturn(Optional.of(pedidoEm(StatusPedido.RECEBIDO)));
        Pedido pedido = pedidoService.cancelar("x1", "Cliente desistiu");
        assertEquals(StatusPedido.CANCELADO, pedido.getStatus());

        when(pedidoRepository.findById("x1")).thenReturn(Optional.of(pedido));
        assertThrows(IllegalStateException.class, () -> pedidoService.avancarStatus("x1", StatusPedido.EM_PREPARACAO));
    }
}
