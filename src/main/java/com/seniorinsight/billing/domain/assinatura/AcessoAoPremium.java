package com.seniorinsight.billing.domain.assinatura;

import java.time.Duration;
import java.time.Instant;

public record AcessoAoPremium(AcessoPlano plano, boolean assinaturaAtiva, Instant fimTrial,
                              Instant fimAcesso, boolean emCarencia) {
    public static final Duration CARENCIA = Duration.ofDays(3);

    public static AcessoAoPremium calcular(Instant inicioTrial, Instant fimTrial, Plano plano, StatusAssinatura status,
                                           Instant inicioPago, Instant fimPago, Instant agora) {
        boolean assinaturaAtiva = plano == Plano.PREMIUM
                && (status == StatusAssinatura.ATIVA || status == StatusAssinatura.INADIMPLENTE);
        if (premiumVigente(plano, status, inicioPago, fimPago, agora)) {
            return new AcessoAoPremium(AcessoPlano.PREMIUM, assinaturaAtiva, fimTrial,
                    fimPagoComCarencia(status, fimPago), status == StatusAssinatura.INADIMPLENTE);
        }
        if (inicioTrial != null && !agora.isBefore(inicioTrial) && agora.isBefore(fimTrial.plus(CARENCIA))) {
            return new AcessoAoPremium(AcessoPlano.TRIAL, assinaturaAtiva, fimTrial, fimTrial.plus(CARENCIA),
                    !agora.isBefore(fimTrial));
        }
        return new AcessoAoPremium(AcessoPlano.FREE, assinaturaAtiva, fimTrial, null, false);
    }

    public static boolean premiumVigente(Plano plano, StatusAssinatura status, Instant inicio, Instant fim,
                                         Instant agora) {
        if (plano == null || plano == Plano.TRIAL || agora.isBefore(inicio)) {
            return false;
        }
        Instant limite = fimPagoComCarencia(status, fim);
        return switch (status) {
            case ATIVA -> limite == null || agora.isBefore(limite);
            case INADIMPLENTE -> limite != null && agora.isBefore(limite);
            default -> false;
        };
    }

    private static Instant fimPagoComCarencia(StatusAssinatura status, Instant fim) {
        return fim != null && status == StatusAssinatura.INADIMPLENTE ? fim.plus(CARENCIA) : fim;
    }
}
