package com.seniorinsight.billing.domain.assinatura;

import java.time.Instant;

/** Direito de acesso do titular a recursos Premium em um instante. Segurança nunca depende dele (RN-014). */
public record AcessoAoPremium(AcessoPlano plano, boolean assinaturaAtiva, Instant fimTrial,
                              Instant fimAcesso, boolean emCarencia) {

    /** {@code trial} e {@code assinatura} podem ser {@code null}. O Premium vigente vale mais que o trial. */
    public static AcessoAoPremium em(Trial trial, Assinatura assinatura, Instant agora) {
        boolean assinaturaAtiva = assinatura != null && assinatura.premiumAtiva();
        Instant fimTrial = trial == null ? null : trial.fim();
        if (assinatura != null && assinatura.vigenteEm(agora)) {
            return new AcessoAoPremium(AcessoPlano.PREMIUM, assinaturaAtiva, fimTrial, assinatura.fimDoAcesso(),
                    assinatura.inadimplente());
        }
        if (trial != null && trial.ativoEm(agora)) {
            return new AcessoAoPremium(AcessoPlano.TRIAL, assinaturaAtiva, fimTrial, trial.fimDaCarencia(),
                    trial.emCarenciaEm(agora));
        }
        return new AcessoAoPremium(AcessoPlano.FREE, assinaturaAtiva, fimTrial, null, false);
    }

    public boolean acessoPremium() {
        return plano != AcessoPlano.FREE;
    }
}
