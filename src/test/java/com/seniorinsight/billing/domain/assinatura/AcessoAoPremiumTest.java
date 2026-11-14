package com.seniorinsight.billing.domain.assinatura;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** HU-041: plano atual (Free, trial ou Premium), fim do teste e carência. Sem Spring. */
class AcessoAoPremiumTest {
    private static final Instant INICIO = Instant.parse("2026-11-01T12:00:00Z");
    private static final Instant FIM = INICIO.plus(14, ChronoUnit.DAYS);
    private static final Trial TRIAL = new Trial(UUID.randomUUID(), INICIO, FIM, false, false, false, false);

    private static Assinatura premium(StatusAssinatura status, Instant fimPeriodo) {
        return new Assinatura(Plano.PREMIUM, status, INICIO, fimPeriodo);
    }

    @Test
    void semTrialNemAssinaturaEFree() {
        var acesso = AcessoAoPremium.em(null, null, INICIO);
        assertThat(acesso.plano()).isEqualTo(AcessoPlano.FREE);
        assertThat(acesso.acessoPremium()).isFalse();
        assertThat(acesso.fimTrial()).isNull();
        assertThat(acesso.fimAcesso()).isNull();
    }

    @Test
    void trialAtivoDaAcessoAteOFimDaCarencia() {
        var acesso = AcessoAoPremium.em(TRIAL, null, INICIO);
        assertThat(acesso.plano()).isEqualTo(AcessoPlano.TRIAL);
        assertThat(acesso.acessoPremium()).isTrue();
        assertThat(acesso.assinaturaAtiva()).isFalse();
        assertThat(acesso.fimTrial()).isEqualTo(FIM);
        assertThat(acesso.fimAcesso()).isEqualTo(FIM.plus(3, ChronoUnit.DAYS));
        assertThat(acesso.emCarencia()).isFalse();
    }

    @Test
    void umInstanteAntesDoFimAindaNaoEstaEmCarencia() {
        assertThat(AcessoAoPremium.em(TRIAL, null, FIM.minusSeconds(1)).emCarencia()).isFalse();
    }

    @Test
    void noFimDoTesteEntraNaCarenciaMantendoPremium() {
        var acesso = AcessoAoPremium.em(TRIAL, null, FIM);
        assertThat(acesso.plano()).isEqualTo(AcessoPlano.TRIAL);
        assertThat(acesso.emCarencia()).isTrue();
        assertThat(acesso.acessoPremium()).isTrue();
    }

    @Test
    void umInstanteAntesDoFimDaCarenciaAindaTemAcesso() {
        var acesso = AcessoAoPremium.em(TRIAL, null, FIM.plus(3, ChronoUnit.DAYS).minusSeconds(1));
        assertThat(acesso.plano()).isEqualTo(AcessoPlano.TRIAL);
        assertThat(acesso.acessoPremium()).isTrue();
    }

    @Test
    void depoisDe3DiasDeCarenciaViraFreeMasLembraOFimDoTeste() {
        var acesso = AcessoAoPremium.em(TRIAL, null, FIM.plus(3, ChronoUnit.DAYS));
        assertThat(acesso.plano()).isEqualTo(AcessoPlano.FREE);
        assertThat(acesso.acessoPremium()).isFalse();
        assertThat(acesso.fimTrial()).isEqualTo(FIM);
        assertThat(acesso.fimAcesso()).isNull();
        assertThat(acesso.emCarencia()).isFalse();
    }

    @Test
    void antesDoInicioDoTrialNaoHaAcesso() {
        assertThat(AcessoAoPremium.em(TRIAL, null, INICIO.minusSeconds(1)).plano()).isEqualTo(AcessoPlano.FREE);
    }

    @Test
    void assinaturaVigenteTemPrioridadeSobreTrial() {
        var acesso = AcessoAoPremium.em(TRIAL, premium(StatusAssinatura.ATIVA, null), INICIO.plus(1, ChronoUnit.DAYS));
        assertThat(acesso.plano()).isEqualTo(AcessoPlano.PREMIUM);
        assertThat(acesso.assinaturaAtiva()).isTrue();
        assertThat(acesso.emCarencia()).isFalse();
        assertThat(acesso.fimAcesso()).isNull();
    }

    @Test
    void premiumContinuaDepoisDoFimDoTrialEDoFimDaCarencia() {
        var acesso = AcessoAoPremium.em(TRIAL, premium(StatusAssinatura.ATIVA, null), FIM.plus(30, ChronoUnit.DAYS));
        assertThat(acesso.plano()).isEqualTo(AcessoPlano.PREMIUM);
        assertThat(acesso.fimTrial()).isEqualTo(FIM);
    }

    @Test
    void premiumInadimplenteTemCarenciaDe3DiasSobreOFimDoPeriodo() {
        Instant fimPeriodo = INICIO.plus(30, ChronoUnit.DAYS);
        var assinatura = premium(StatusAssinatura.INADIMPLENTE, fimPeriodo);
        var naCarencia = AcessoAoPremium.em(null, assinatura, fimPeriodo.plus(1, ChronoUnit.DAYS));
        assertThat(naCarencia.plano()).isEqualTo(AcessoPlano.PREMIUM);
        assertThat(naCarencia.emCarencia()).isTrue();
        assertThat(naCarencia.assinaturaAtiva()).isTrue();
        assertThat(naCarencia.fimAcesso()).isEqualTo(fimPeriodo.plus(3, ChronoUnit.DAYS));
        var vencida = AcessoAoPremium.em(null, assinatura, fimPeriodo.plus(3, ChronoUnit.DAYS));
        assertThat(vencida.plano()).isEqualTo(AcessoPlano.FREE);
    }

    @Test
    void assinaturaCanceladaOuExpiradaNaoConcedePremium() {
        for (var status : new StatusAssinatura[] {StatusAssinatura.CANCELADA, StatusAssinatura.EXPIRADA}) {
            var acesso = AcessoAoPremium.em(null, premium(status, null), INICIO.plus(1, ChronoUnit.DAYS));
            assertThat(acesso.plano()).as(status.name()).isEqualTo(AcessoPlano.FREE);
            assertThat(acesso.assinaturaAtiva()).isFalse();
        }
    }

    @Test
    void assinaturaCanceladaDeixaOTrialVigenteValer() {
        var acesso = AcessoAoPremium.em(TRIAL, premium(StatusAssinatura.CANCELADA, null), INICIO.plus(1, ChronoUnit.DAYS));
        assertThat(acesso.plano()).isEqualTo(AcessoPlano.TRIAL);
    }

    @Test
    void assinaturaQueAindaNaoComecouNaoConcedePremium() {
        var futura = new Assinatura(Plano.PREMIUM, StatusAssinatura.ATIVA, INICIO.plus(5, ChronoUnit.DAYS), null);
        assertThat(AcessoAoPremium.em(null, futura, INICIO).plano()).isEqualTo(AcessoPlano.FREE);
    }

    @Test
    void planoB2bAtivoConcedePremium() {
        var b2b = new Assinatura(Plano.B2B, StatusAssinatura.ATIVA, INICIO, null);
        assertThat(AcessoAoPremium.em(null, b2b, INICIO).plano()).isEqualTo(AcessoPlano.PREMIUM);
    }
}
