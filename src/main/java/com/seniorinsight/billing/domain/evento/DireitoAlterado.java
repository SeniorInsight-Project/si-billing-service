package com.seniorinsight.billing.domain.evento;

import java.time.Instant;
import java.util.UUID;

import com.seniorinsight.billing.domain.assinatura.AcessoPlano;

public record DireitoAlterado(UUID trialId, String familiarSub, AcessoPlano plano, boolean ativa,
                              Instant expiraEm, Instant fimTrialEm, boolean emCarencia) implements EventoDominio {
    public static final String TIPO = "subscription.changed";

    @Override public String tipoAgregado() { return "trial"; }
    @Override public UUID agregadoId() { return trialId; }
    @Override public String tipoEvento() { return TIPO; }
}
