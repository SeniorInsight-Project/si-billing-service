package com.seniorinsight.billing.domain.assinatura;

import java.time.Duration;
import java.time.Instant;

/** Assinatura paga da família e as regras de vigência do Premium. */
public record Assinatura(Plano plano, StatusAssinatura status, Instant inicio, Instant fimPeriodo) {
    public static final Duration CARENCIA_INADIMPLENCIA = Duration.ofDays(3);

    /** Premium pago, ativo ou inadimplente, que ainda concede acesso. */
    public boolean vigenteEm(Instant agora) {
        if (plano == null || plano == Plano.TRIAL || agora.isBefore(inicio)) {
            return false;
        }
        Instant limite = fimDoAcesso();
        return switch (status) {
            case ATIVA -> limite == null || agora.isBefore(limite);
            case INADIMPLENTE -> limite != null && agora.isBefore(limite);
            default -> false;
        };
    }

    public boolean premiumAtiva() {
        return plano == Plano.PREMIUM && (status == StatusAssinatura.ATIVA || status == StatusAssinatura.INADIMPLENTE);
    }

    public boolean inadimplente() {
        return status == StatusAssinatura.INADIMPLENTE;
    }

    /** Fim do período pago; na inadimplência, acrescido da carência. {@code null} quando não há fim. */
    public Instant fimDoAcesso() {
        return fimPeriodo != null && inadimplente() ? fimPeriodo.plus(CARENCIA_INADIMPLENCIA) : fimPeriodo;
    }
}
