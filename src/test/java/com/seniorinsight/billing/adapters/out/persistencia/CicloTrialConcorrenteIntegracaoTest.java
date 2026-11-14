package com.seniorinsight.billing.adapters.out.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.seniorinsight.billing.TesteIntegracao;
import com.seniorinsight.billing.application.porta.saida.TrialRepositorio;
import com.seniorinsight.billing.application.trial.DireitoService;
import com.seniorinsight.billing.application.trial.ProcessarCicloTrial;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Duas execuções simultâneas do ciclo publicam cada evento uma única vez. Sem transação de teste. */
@TesteIntegracao
class CicloTrialConcorrenteIntegracaoTest {
    @Autowired ProcessarCicloTrial ciclo;
    @Autowired DireitoService direitos;
    @Autowired TrialRepositorio trials;
    @Autowired JdbcTemplate jdbc;

    private final String sub = UUID.randomUUID().toString();

    @AfterEach
    void limpar() {
        jdbc.update("DELETE FROM outbox_evento WHERE payload->>'familiarSub' = ?", sub);
        jdbc.update("DELETE FROM uso_trial WHERE titular_sub = ?", sub);
    }

    @Test
    void duasExecucoesDoCicloPublicamUmUnicoLembrete() throws Exception {
        Instant agora = Instant.now();
        direitos.iniciar(sub, agora.minus(Duration.ofDays(13)));

        simultaneamente(2, () -> {
            ciclo.publicarLembretes(agora);
            return null;
        });

        assertThat(jdbc.queryForList("""
                SELECT payload->>'diasRestantes' FROM outbox_evento
                 WHERE payload->>'familiarSub' = ? AND tipo_evento = 'trial.expiring'
                """, String.class, sub)).containsExactly("1");
        assertThat(jdbc.queryForObject("SELECT aviso_um_dia_em IS NOT NULL FROM uso_trial WHERE titular_sub = ?",
                Boolean.class, sub)).isTrue();
    }

    @Test
    void duasExecucoesDoCicloPublicamUmUnicoFim() throws Exception {
        Instant agora = Instant.now();
        direitos.iniciar(sub, agora.minus(Duration.ofDays(18)));

        simultaneamente(2, () -> {
            ciclo.atualizarCiclos(agora);
            return null;
        });

        assertThat(jdbc.queryForList("""
                SELECT payload->>'plano' FROM outbox_evento
                 WHERE payload->>'familiarSub' = ? AND tipo_evento = 'subscription.changed'
                """, String.class, sub)).containsExactlyInAnyOrder("TRIAL", "FREE");
    }

    @Test
    void marcacoesCondicionaisSoAtualizamUmaVez() throws Exception {
        Instant agora = Instant.now();
        var trial = direitos.iniciar(sub, agora);
        UUID id = jdbc.queryForObject("SELECT id FROM uso_trial WHERE titular_sub = ?", UUID.class, sub);
        assertThat(trial).isNotNull();

        assertThat(simultaneamente(8, () -> trials.marcarLembrete(id, 1, agora))).containsOnlyOnce(true);
        assertThat(simultaneamente(8, () -> trials.marcarLembrete(id, 3, agora))).containsOnlyOnce(true);
        assertThat(simultaneamente(8, () -> trials.marcarFim(id, agora))).containsOnlyOnce(true);
    }

    private static <T> java.util.List<T> simultaneamente(int vezes, Callable<T> tarefa) throws Exception {
        var largada = new CountDownLatch(1);
        try (var exec = Executors.newFixedThreadPool(vezes)) {
            var futuros = new ArrayList<java.util.concurrent.Future<T>>();
            for (int i = 0; i < vezes; i++) {
                futuros.add(exec.submit(() -> {
                    largada.await();
                    return tarefa.call();
                }));
            }
            largada.countDown();
            var resultados = new ArrayList<T>();
            for (var futuro : futuros) {
                resultados.add(futuro.get(15, TimeUnit.SECONDS));
            }
            return resultados;
        }
    }
}
