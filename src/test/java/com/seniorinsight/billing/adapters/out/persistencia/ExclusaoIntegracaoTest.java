package com.seniorinsight.billing.adapters.out.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import com.seniorinsight.billing.TesteIntegracao;
import com.seniorinsight.billing.application.lgpd.TratarExclusao;
import com.seniorinsight.billing.application.porta.saida.ExclusaoDadosRepositorio;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@TesteIntegracao
@Transactional
class ExclusaoIntegracaoTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entidades;
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
    void premiumAtivoBloqueiaExclusaoEAssinaturaContinuaDisponivel() {
        String sub = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO assinatura (titular_sub, plano, status, inicio_em) VALUES (?, 'PREMIUM', 'ATIVA', now())",
                sub);

        assertThat(repositorio.assinaturaAtiva(sub)).isTrue();
        assertThatThrownBy(() -> repositorio.bloquearCobranca(sub)).hasMessageContaining("aguardando cancelamento");
        assertThat(repositorio.assinaturaAtiva(sub)).isTrue();
    }

    @Test
    void idosoSoConfirmaESemApagarDadosLocais() {
        UUID titular = UUID.randomUUID();
        UUID protocolo = UUID.randomUUID();
        String sub = titular.toString();
        jdbc.update("INSERT INTO assinatura (titular_sub, plano, status, inicio_em) VALUES (?, 'TRIAL', 'EXPIRADA', now())",
                sub);
        jdbc.update("INSERT INTO uso_trial (titular_sub, iniciado_em, expira_em) VALUES (?, now(), now() + interval '1 day')",
                sub);

        enviar(protocolo, titular, "IDOSO", sub);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM assinatura WHERE titular_sub = ?", Integer.class, sub)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uso_trial WHERE titular_sub = ?", Integer.class, sub)).isEqualTo(1);
        confirmar(protocolo);
    }

    @Test
    void stripeDesligadoComReferenciasMantemDadosParaNovaTentativa() {
        String sub = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO assinatura (titular_sub, plano, status, inicio_em, stripe_customer_id, stripe_subscription_id)
                VALUES (?, 'TRIAL', 'EXPIRADA', now(), 'cus_teste', 'sub_teste')
                """, sub);

        assertThatThrownBy(() -> tratador.tratar(new TratarExclusao.Pedido(
                UUID.randomUUID(), UUID.randomUUID(), sub, false)))
                .hasMessageContaining("Stripe ainda não habilitada");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM assinatura WHERE titular_sub = ?", Integer.class, sub)).isEqualTo(1);
    }
    private void confirmar(UUID protocolo) {
        entidades.flush();
        assertThat(jdbc.queryForList("""
                SELECT payload->>'protocolo' FROM outbox_evento
                WHERE agregado_id = ? AND tipo_evento = 'lgpd.erasure.completed'
                """, String.class, protocolo)).containsExactly(protocolo.toString());
    }

    private void enviar(UUID protocolo, UUID titular, String tipo, String sub) {
        tratador.tratar(new TratarExclusao.Pedido(protocolo, titular, sub, "IDOSO".equals(tipo)));
    }
}
