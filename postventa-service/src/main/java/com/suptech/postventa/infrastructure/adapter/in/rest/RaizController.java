package com.suptech.postventa.infrastructure.adapter.in.rest;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RaizController {

    @GetMapping("/")
    public String indice() {
        return "redirect:/swagger-ui.html";
    }
}
