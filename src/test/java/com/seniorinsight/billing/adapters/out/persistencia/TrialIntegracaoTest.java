package com.seniorinsight.billing.adapters.out.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.seniorinsight.billing.TesteIntegracao;
import com.seniorinsight.billing.application.trial.DireitoService;
import com.seniorinsight.billing.domain.assinatura.AcessoPlano;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@TesteIntegracao
@Transactional
class TrialIntegracaoTest {
    private static final Instant INICIO = Instant.parse("2026-11-01T12:00:00Z");
    private static final Instant FIM = INICIO.plus(Duration.ofDays(14));
    @Autowired DireitoService direitos;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entidade;

    @Test
    void umTrialPorFamiliaComCarenciaEAvisoDeFim() {
        String sub = UUID.randomUUID().toString();
        var inicial = direitos.iniciar(sub, INICIO);
        assertThat(inicial.plano()).isEqualTo(AcessoPlano.TRIAL);
        assertThat(inicial.acessoPremium()).isTrue();
        assertThat(inicial.assinaturaAtiva()).isFalse();
        assertThat(inicial.fimTrialEm()).isEqualTo(FIM);
        assertThat(inicial.acessoAte()).isEqualTo(FIM.plus(Duration.ofDays(3)));
        assertThat(direitos.iniciar(sub, INICIO.plusSeconds(1))).isEqualTo(inicial);

        direitos.atualizarCiclo(sub, FIM);
        direitos.atualizarCiclo(sub, FIM.plusSeconds(10));
        assertThat(direitos.consultar(sub, FIM).emCarencia()).isTrue();
        assertThat(direitos.consultar(sub, FIM.plus(Duration.ofDays(3)).minusSeconds(1)).acessoPremium()).isTrue();

        var fimCarencia = FIM.plus(Duration.ofDays(3));
        direitos.atualizarCiclo(sub, fimCarencia);
        direitos.atualizarCiclo(sub, fimCarencia.plusSeconds(1));
        var encerrado = direitos.iniciar(sub, fimCarencia.plusSeconds(5));
        assertThat(encerrado.plano()).isEqualTo(AcessoPlano.FREE);
        assertThat(encerrado.acessoPremium()).isFalse();
        assertThat(encerrado.fimTrialEm()).isEqualTo(FIM);

        entidade.flush();
        assertThat(jdbc.queryForList("""
                SELECT (payload->>'plano') || '/' || (payload->>'emCarencia') || '/' || (payload->>'ativa')
                  FROM outbox_evento WHERE payload->>'familiarSub' = ? AND tipo_evento = 'subscription.changed'
                """, String.class, sub)).containsExactlyInAnyOrder("TRIAL/false/true", "TRIAL/true/true", "FREE/false/false");
    }

    @Test
    void avisaTresEUmDiaUmaVezEPulaQuemAssinou() {
        String semAssinatura = UUID.randomUUID().toString();
        String assinante = UUID.randomUUID().toString();
        direitos.iniciar(semAssinatura, INICIO);
        direitos.iniciar(assinante, INICIO);
        jdbc.update("""
                INSERT INTO assinatura (titular_sub, plano, status, inicio_em) VALUES (?, 'PREMIUM', 'ATIVA', ?)
                """, assinante, INICIO.atOffset(java.time.ZoneOffset.UTC));

        for (int dias : List.of(4, 3, 3, 2, 1, 1, 0)) {
            direitos.lembrar(semAssinatura, FIM.minus(Duration.ofDays(dias)));
            direitos.lembrar(assinante, FIM.minus(Duration.ofDays(dias)));
        }

        entidade.flush();
        assertThat(lembretes(semAssinatura)).containsExactlyInAnyOrder("3", "1");
        assertThat(lembretes(assinante)).isEmpty();
        assertThat(direitos.consultar(assinante, FIM).plano()).isEqualTo(AcessoPlano.PREMIUM);
    }

    private List<String> lembretes(String sub) {
        return jdbc.queryForList("""
                SELECT payload->>'diasRestantes' FROM outbox_evento
                 WHERE payload->>'familiarSub' = ? AND tipo_evento = 'trial.expiring'
                """, String.class, sub);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void duasExecucoesSimultaneasPublicamUmLembrete() throws Exception {
        String sub = UUID.randomUUID().toString();
        Instant agora = Instant.now();
        direitos.iniciar(sub, agora);
        var partida = new CountDownLatch(1);
        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            var primeiro = exec.submit(() -> {
                partida.await();
                direitos.lembrar(sub, agora.plus(Duration.ofDays(11)));
                return null;
            });
            var segundo = exec.submit(() -> {
                partida.await();
                direitos.lembrar(sub, agora.plus(Duration.ofDays(11)));
                return null;
            });
            partida.countDown();
            primeiro.get(10, TimeUnit.SECONDS);
            segundo.get(10, TimeUnit.SECONDS);
            assertThat(lembretes(sub)).containsExactly("3");
        } finally {
            jdbc.update("DELETE FROM outbox_evento WHERE payload->>'familiarSub' = ?", sub);
            jdbc.update("DELETE FROM uso_trial WHERE titular_sub = ?", sub);
        }
    }
}
