package com.seniorinsight.billing.adapters.out.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


import java.util.UUID;

import com.seniorinsight.billing.TesteIntegracao;
import com.seniorinsight.billing.application.lgpd.TratarExclusao;

import com.seniorinsight.billing.application.porta.saida.ExclusaoDadosRepositorio;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TesteIntegracao
@Transactional
class ExclusaoIntegracaoTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entidades;
    @Autowired MockMvc mvc;
    @Autowired TratarExclusao tratador;
    @Autowired ExclusaoDadosRepositorio repositorio;

    @Test
    void removeCobrancaLocalEConfirmaUmaVezPorProtocolo() {
        UUID titular = UUID.randomUUID();
        UUID protocolo = UUID.randomUUID();
        String sub = titular.toString();
        jdbc.update("INSERT INTO assinatura (titular_sub, plano, status, inicio_em) VALUES (?, 'TRIAL', 'EXPIRADA', now())",
                sub);
        jdbc.update("INSERT INTO uso_trial (titular_sub, iniciado_em, expira_em) VALUES (?, now(), now() + interval '1 day')",
                sub);

        enviar(protocolo, titular, "FAMILIAR", sub);
        enviar(protocolo, titular, "FAMILIAR", sub);

        assertThat(jdbc.queryForList("SELECT id FROM assinatura WHERE titular_sub = ?", UUID.class, sub)).isEmpty();
        assertThat(jdbc.queryForList("SELECT id FROM uso_trial WHERE titular_sub = ?", UUID.class, sub)).isEmpty();
        confirmar(protocolo);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO uso_trial (titular_sub, iniciado_em, expira_em) VALUES (?, now(), now() + interval '1 day')
                """, sub)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void premiumAtivoBloqueiaExclusaoEAssinaturaContinuaDisponivel() throws Exception {
        String sub = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO assinatura (titular_sub, plano, status, inicio_em) VALUES (?, 'PREMIUM', 'ATIVA', now())",
                sub);

        assertThat(repositorio.assinaturaAtiva(sub)).isTrue();
        mvc.perform(get("/v1/direito").with(jwt().jwt(token -> token.subject(sub))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assinaturaAtiva").value(true));
        mvc.perform(get("/v1/direito").with(jwt().jwt(token -> token.subject("outro-titular"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assinaturaAtiva").value(false));
        assertThatThrownBy(() -> repositorio.excluir(sub)).hasMessageContaining("aguardando cancelamento");
        assertThat(repositorio.assinaturaAtiva(sub)).isTrue();
    }
    private void confirmar(UUID protocolo) {
        entidades.flush();
        assertThat(jdbc.queryForList("""
                SELECT payload->>'protocolo' FROM outbox_evento
                WHERE agregado_id = ? AND tipo_evento = 'lgpd.erasure.completed'
                """, String.class, protocolo)).containsExactly(protocolo.toString());
    }

    private void enviar(UUID protocolo, UUID titular, String tipo, String sub) {
        UUID mensagem = UUID.randomUUID();
        String corpo = """
                {"specversion":"1.0","id":"%s","source":"/report-service","type":"lgpd.erasure.requested",
                 "time":"2026-11-08T15:30:00Z","datacontenttype":"application/json",
                 "dataschema":"https://raw.githubusercontent.com/SeniorInsight-Project/si-contracts/main/events/lgpd.erasure.requested.v1.json",
                 "subject":"%s","data":{"protocolo":"%s","titularId":"%s","tipoTitular":"%s","titularSub":"%s"}}
                """.formatted(mensagem, protocolo, protocolo, titular, tipo, sub);
        tratador.tratar(mensagem, corpo);
    }
}
