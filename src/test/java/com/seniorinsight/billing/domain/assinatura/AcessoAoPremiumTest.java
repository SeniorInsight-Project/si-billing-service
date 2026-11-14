package com.seniorinsight.billing.domain.assinatura;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class AcessoAoPremiumTest {
    private static final Instant INICIO = Instant.parse("2026-11-01T12:00:00Z");
    private static final Instant FIM = INICIO.plus(14, ChronoUnit.DAYS);

    @Test
    void primeiroCadastroAtivaQuatorzeDiasDeTrial() {
        var acesso = AcessoAoPremium.calcular(INICIO, FIM, null, null, null, null, INICIO);
        assertThat(acesso.plano()).isEqualTo(AcessoPlano.TRIAL);
        assertThat(acesso.fimTrial()).isEqualTo(FIM);
        assertThat(acesso.emCarencia()).isFalse();
    }

    @Test
    void mantemTrialNaCarenciaEViraFreeNoLimite() {
        var carencia = AcessoAoPremium.calcular(INICIO, FIM, null, null, null, null, FIM);
        assertThat(carencia.emCarencia()).isTrue();
        assertThat(carencia.plano()).isEqualTo(AcessoPlano.TRIAL);
        var free = AcessoAoPremium.calcular(INICIO, FIM, null, null, null, null, FIM.plus(3, ChronoUnit.DAYS));
        assertThat(free.plano()).isEqualTo(AcessoPlano.FREE);
    }

    @Test
    void assinaturaVigenteTemPrioridadeSobreTrial() {
        var acesso = AcessoAoPremium.calcular(INICIO, FIM, Plano.PREMIUM, StatusAssinatura.ATIVA, INICIO, null, FIM);
        assertThat(acesso.plano()).isEqualTo(AcessoPlano.PREMIUM);
        assertThat(acesso.assinaturaAtiva()).isTrue();
    }
}
