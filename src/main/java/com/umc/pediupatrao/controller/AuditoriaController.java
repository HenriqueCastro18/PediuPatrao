package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.service.AuditoriaService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/auditoria")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    public String listar(@RequestParam(required = false) String entidade, Model model) {
        List<Auditoria> registros;
        if (entidade != null && !entidade.isBlank()) {
            registros = auditoriaService.listarPorEntidade(entidade);
        } else {
            registros = auditoriaService.listarTodos();
        }
        model.addAttribute("registros", registros);
        model.addAttribute("filtroEntidade", entidade);
        model.addAttribute("content", "auditoria/lista :: content");
        return "layout";
    }
}
