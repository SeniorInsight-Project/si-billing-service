package com.seniorinsight.billing.domain.evento;

import java.time.Instant;
import java.util.UUID;

public record TrialPertoDoFim(UUID trialId, String familiarSub, Instant fimTrialEm, int diasRestantes)
        implements EventoDominio {
    public static final String TIPO = "trial.expiring";

    @Override public String tipoAgregado() { return "trial"; }
    @Override public UUID agregadoId() { return trialId; }
    @Override public String tipoEvento() { return TIPO; }
}
