package com.seniorinsight.billing.adapters.in.agendamento;

import java.time.Instant;

import com.seniorinsight.billing.application.trial.ProcessarCicloTrial;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class CicloTrialJob {
    private final ProcessarCicloTrial ciclo;

    CicloTrialJob(ProcessarCicloTrial ciclo) {
        this.ciclo = ciclo;
    }

    @Scheduled(fixedDelayString = "${billing.trial.intervalo-ciclo}")
    void atualizarCiclos() {
        ciclo.atualizarCiclos(Instant.now());
    }

    @Scheduled(cron = "${billing.trial.cron-lembretes}", zone = "${billing.trial.fuso}")
    void publicarLembretes() {
        ciclo.publicarLembretes(Instant.now());
    }
}
