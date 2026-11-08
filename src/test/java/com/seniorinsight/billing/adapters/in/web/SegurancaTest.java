package com.seniorinsight.billing.adapters.in.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.seniorinsight.billing.TesteIntegracao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TesteIntegracao
class SegurancaTest {

    @Autowired
    MockMvc mvc;

    @Test
    void rotaProtegidaSemTokenRetorna401() throws Exception {
        mvc.perform(get("/v1/assinaturas/minha"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
    }

    @Test
    void rotaProtegidaComTokenPassaPelaSeguranca() throws Exception {
        // Ainda não há endpoints de negócio: com token válido a requisição passa pela segurança e cai no 404.
        mvc.perform(get("/v1/assinaturas/minha").with(jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void soClienteCareComEscopoPodeIniciarEOAplicativoVeApenasSeuDireito() throws Exception {
        String sub = UUID.randomUUID().toString();
        String trial = "/internal/familias/" + sub + "/trial";
        var escopo = new SimpleGrantedAuthority("SCOPE_billing.trial.write");

        mvc.perform(post(trial).with(jwt().jwt(j -> j.subject(sub))))
                .andExpect(status().isForbidden());
        mvc.perform(post(trial).with(jwt().jwt(j -> j.claim("azp", "outro-servico")).authorities(escopo)))
                .andExpect(status().isForbidden());
        mvc.perform(post(trial).with(jwt().jwt(j -> j.claim("azp", "care-service")).authorities(escopo)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.plano").value("TRIAL"));
        mvc.perform(get("/v1/direito").with(jwt().jwt(j -> j.subject(sub))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.acessoPremium").value(true));
        mvc.perform(get("/v1/direito").with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.plano").value("FREE"));
    }

    @Test
    void healthEhPublico() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void swaggerEhPublico() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void demaisEndpointsDoActuatorExigemToken() throws Exception {
        mvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
    }
}
