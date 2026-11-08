package com.seniorinsight.billing.adapters.in.web;

import java.time.Instant;

import com.seniorinsight.billing.application.trial.DireitoService;
import com.seniorinsight.billing.application.trial.DireitoService.Direito;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class DireitoAssinaturaController {

    private final DireitoService direitos;
    private final String clienteCare;

    DireitoAssinaturaController(DireitoService direitos,
                                @Value("${billing.trial.cliente-care}") String clienteCare) {
        this.direitos = direitos;
        this.clienteCare = clienteCare;
    }

    @GetMapping("/v1/direito")
    Direito consultar(@AuthenticationPrincipal Jwt jwt) {
        if (jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new AccessDeniedException("Token sem titular");
        }
        return direitos.consultar(jwt.getSubject(), Instant.now());
    }

    @PostMapping("/internal/familias/{sub}/trial")
    Direito iniciar(@PathVariable String sub, @AuthenticationPrincipal Jwt jwt) {
        if (!clienteCare.equals(jwt.getClaimAsString("azp"))) {
            throw new AccessDeniedException("Cliente não autorizado a iniciar o trial");
        }
        if (sub.isBlank() || sub.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador de família inválido");
        }
        try {
            return direitos.iniciar(sub, Instant.now());
        } catch (DireitoService.FamiliaExcluida falha) {
            throw new ResponseStatusException(HttpStatus.GONE, "Família excluída");
        }
    }
}
