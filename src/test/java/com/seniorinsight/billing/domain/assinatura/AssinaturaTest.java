package com.seniorinsight.billing.domain.assinatura;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AssinaturaTest {
    private static final Instant INICIO = Instant.parse("2026-11-01T12:00:00Z");
    private static final Instant FIM = INICIO.plus(Duration.ofDays(30));

    @Test
    void ativaSemFimDePeriodoEVigente() {
        assertThat(new Assinatura(Plano.PREMIUM, StatusAssinatura.ATIVA, INICIO, null).vigenteEm(INICIO)).isTrue();
    }

    @Test
    void ativaVaiAteOFimDoPeriodo() {
        var assinatura = new Assinatura(Plano.PREMIUM, StatusAssinatura.ATIVA, INICIO, FIM);
        assertThat(assinatura.vigenteEm(FIM.minusSeconds(1))).isTrue();
        assertThat(assinatura.vigenteEm(FIM)).isFalse();
    }

    @Test
    void inadimplenteTemCarenciaDe3Dias() {
        var assinatura = new Assinatura(Plano.PREMIUM, StatusAssinatura.INADIMPLENTE, INICIO, FIM);
        assertThat(assinatura.vigenteEm(FIM.plus(Duration.ofDays(3)).minusSeconds(1))).isTrue();
        assertThat(assinatura.vigenteEm(FIM.plus(Duration.ofDays(3)))).isFalse();
        assertThat(assinatura.fimDoAcesso()).isEqualTo(FIM.plus(Duration.ofDays(3)));
    }

    @Test
    void inadimplenteSemFimDePeriodoNaoConcedeAcesso() {
        assertThat(new Assinatura(Plano.PREMIUM, StatusAssinatura.INADIMPLENTE, INICIO, null).vigenteEm(INICIO)).isFalse();
    }

    @Test
    void canceladaEExpiradaNaoSaoVigentes() {
        assertThat(new Assinatura(Plano.PREMIUM, StatusAssinatura.CANCELADA, INICIO, FIM).vigenteEm(INICIO)).isFalse();
        assertThat(new Assinatura(Plano.PREMIUM, StatusAssinatura.EXPIRADA, INICIO, FIM).vigenteEm(INICIO)).isFalse();
    }

    @Test
    void planoTrialDaTabelaDeAssinaturaNaoContaComoPago() {
        assertThat(new Assinatura(Plano.TRIAL, StatusAssinatura.ATIVA, INICIO, null).vigenteEm(INICIO)).isFalse();
    }

    @Test
    void naoVigenteAntesDoInicio() {
        assertThat(new Assinatura(Plano.PREMIUM, StatusAssinatura.ATIVA, INICIO, null).vigenteEm(INICIO.minusSeconds(1)))
                .isFalse();
    }
}
