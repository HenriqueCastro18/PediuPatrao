package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.FormaPagamento;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.ProdutoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/pedidos")
public class PedidoWebController {

    private final PedidoService pedidoService;
    private final ClienteService clienteService;
    private final ProdutoService produtoService;

    public PedidoWebController(PedidoService pedidoService, ClienteService clienteService, ProdutoService produtoService) {
        this.pedidoService = pedidoService;
        this.clienteService = clienteService;
        this.produtoService = produtoService;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("pedidos", pedidoService.listarPedidos());
        model.addAttribute("content", "pedidos/lista :: content");
        return "layout";
    }

    @GetMapping("/novo")
    public String novo(Model model) {
        model.addAttribute("clientes", clienteService.listarClientes());
        model.addAttribute("produtos", produtoService.listarAtivos());
        model.addAttribute("formasPagamento", FormaPagamento.values());
        model.addAttribute("content", "pedidos/form :: content");
        return "layout";
    }

    @PostMapping("/salvar")
    public String salvar(@RequestParam String clienteId,
                         @RequestParam(required = false) List<String> produtoId,
                         @RequestParam(required = false) List<Integer> quantidade,
                         @RequestParam(required = false) String formaPagamento,
                         @RequestParam(required = false) Double valorRecebido,
                         RedirectAttributes redirect) {
        Pedido pedido = pedidoService.criar(clienteId, produtoId, quantidade, formaPagamento, valorRecebido);
        redirect.addFlashAttribute("sucesso", "Pedido registrado com sucesso!");
        return "redirect:/pedidos/" + pedido.getId();
    }

    @GetMapping("/{id}")
    public String detalhe(@PathVariable String id, Model model) {
        Pedido pedido = pedidoService.buscarPorId(id);
        model.addAttribute("pedido", pedido);
        model.addAttribute("proximos", pedido.getStatus().proximosPermitidos());
        model.addAttribute("content", "pedidos/detalhe :: content");
        return "layout";
    }

    @PostMapping("/{id}/status")
    public String status(@PathVariable String id, @RequestParam StatusPedido novoStatus, RedirectAttributes redirect) {
        pedidoService.avancarStatus(id, novoStatus);
        redirect.addFlashAttribute("sucesso", "Status atualizado para " + novoStatus.getDescricao() + ".");
        return "redirect:/pedidos/" + id;
    }

    @PostMapping("/{id}/desconto")
    public String desconto(@PathVariable String id, @RequestParam double percentual, RedirectAttributes redirect) {
        pedidoService.aplicarDesconto(id, percentual);
        redirect.addFlashAttribute("sucesso", "Desconto aplicado.");
        return "redirect:/pedidos/" + id;
    }

    @PostMapping("/{id}/cancelar")
    public String cancelar(@PathVariable String id, @RequestParam String justificativa, RedirectAttributes redirect) {
        pedidoService.cancelar(id, justificativa);
        redirect.addFlashAttribute("sucesso", "Pedido cancelado.");
        return "redirect:/pedidos/" + id;
    }
}
