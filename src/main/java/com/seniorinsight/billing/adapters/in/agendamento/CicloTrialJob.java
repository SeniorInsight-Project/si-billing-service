package com.seniorinsight.billing.adapters.in.agendamento;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import com.seniorinsight.billing.application.porta.saida.TrialRepositorio;
import com.seniorinsight.billing.application.trial.DireitoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class CicloTrialJob {
    private static final Logger LOG = LoggerFactory.getLogger(CicloTrialJob.class);
    private final TrialRepositorio trials;
    private final DireitoService direitos;
    private final ZoneId fuso;

    CicloTrialJob(TrialRepositorio trials, DireitoService direitos, @Value("${billing.trial.fuso}") String fuso) {
        this.trials = trials;
        this.direitos = direitos;
        this.fuso = ZoneId.of(fuso);
    }

    @Scheduled(fixedDelayString = "${billing.trial.intervalo-ciclo}")
    void atualizarCiclos() {
        Instant agora = Instant.now();
        processar(cursor -> trials.mudancasPendentes(agora, agora.minus(DireitoService.CARENCIA), cursor),
                sub -> direitos.atualizarCiclo(sub, agora));
    }

    @Scheduled(cron = "${billing.trial.cron-lembretes}", zone = "${billing.trial.fuso}")
    void publicarLembretes() {
        Instant agora = Instant.now();
        var hoje = agora.atZone(fuso).toLocalDate();
        processar(cursor -> trials.lembretesPendentes(
                hoje.plusDays(1).atStartOfDay(fuso).toInstant(), hoje.plusDays(2).atStartOfDay(fuso).toInstant(),
                hoje.plusDays(3).atStartOfDay(fuso).toInstant(), hoje.plusDays(4).atStartOfDay(fuso).toInstant(),
                cursor), sub -> direitos.lembrar(sub, agora));
    }

    private void processar(Function<String, List<String>> pagina, Consumer<String> tarefa) {
        String cursor = "";
        List<String> lote = pagina.apply(cursor);
        while (!lote.isEmpty()) {
            for (String sub : lote) {
                try {
                    tarefa.accept(sub);
                } catch (RuntimeException falha) {
                    LOG.warn("Transição de trial não gravada ({})", falha.getClass().getSimpleName());
                }
            }
            cursor = lote.getLast();
            lote = pagina.apply(cursor);
        }
    }
}
