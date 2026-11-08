package com.seniorinsight.billing.adapters.in.web;

import com.seniorinsight.billing.application.porta.saida.ExclusaoDadosRepositorio;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class DireitoAssinaturaController {

    private final ExclusaoDadosRepositorio assinaturas;

    DireitoAssinaturaController(ExclusaoDadosRepositorio assinaturas) {
        this.assinaturas = assinaturas;
    }

    @GetMapping("/v1/direito")
    Direito consultar(@AuthenticationPrincipal Jwt jwt) {
        return new Direito(assinaturas.assinaturaAtiva(jwt.getSubject()));
    }

    record Direito(boolean assinaturaAtiva) {
    }
}
