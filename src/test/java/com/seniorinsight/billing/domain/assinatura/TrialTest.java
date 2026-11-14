package com.seniorinsight.billing.domain.assinatura;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import com.seniorinsight.billing.domain.assinatura.Trial.Transicao;

/** HU-040 (início único) e HU-042 (fim do teste, carência e avisos). Sem Spring. */
class TrialTest {
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final Instant INICIO = Instant.parse("2026-11-01T15:00:00Z");
    private static final Instant FIM = Trial.fimPara(INICIO);
    private static final Instant FIM_CARENCIA = FIM.plus(Duration.ofDays(3));

    private static Trial novo() {
        return new Trial(UUID.randomUUID(), INICIO, FIM, false, false, false, false);
    }

    private static Trial com(boolean carencia, boolean fim, boolean tres, boolean um) {
        return new Trial(UUID.randomUUID(), INICIO, FIM, carencia, fim, tres, um);
    }

    private static Assinatura premiumAtiva() {
        return new Assinatura(Plano.PREMIUM, StatusAssinatura.ATIVA, INICIO, null);
    }

    // HU-040

    @Test
    void trialDura14Dias() {
        assertThat(FIM).isEqualTo(INICIO.plus(Duration.ofDays(14)));
    }

    @Test
    void familiaSemHistoricoPodeIniciar() {
        assertThat(Trial.podeIniciar(null, null, INICIO)).isTrue();
    }

    @Test
    void trialSoPodeIniciarUmaVezMesmoDepoisDeEncerrado() {
        assertThat(Trial.podeIniciar(novo(), null, INICIO.plusSeconds(1))).isFalse();
        assertThat(Trial.podeIniciar(novo(), null, FIM_CARENCIA.plus(Duration.ofDays(60)))).isFalse();
    }

    @Test
    void quemJaTemPremiumVigenteNaoIniciaTrial() {
        assertThat(Trial.podeIniciar(null, premiumAtiva(), INICIO)).isFalse();
    }

    @Test
    void quemTemAssinaturaCanceladaPodeIniciar() {
        var cancelada = new Assinatura(Plano.PREMIUM, StatusAssinatura.CANCELADA, INICIO, null);
        assertThat(Trial.podeIniciar(null, cancelada, INICIO)).isTrue();
    }

    // HU-042: fim do teste e carência

    @Test
    void antesDoFimNaoHaTransicao() {
        assertThat(novo().transicaoEm(FIM.minusSeconds(1))).isEqualTo(Transicao.NENHUMA);
    }

    @Test
    void noFimPublicaACarencia() {
        assertThat(novo().transicaoEm(FIM)).isEqualTo(Transicao.CARENCIA);
        assertThat(novo().transicaoEm(FIM_CARENCIA.minusSeconds(1))).isEqualTo(Transicao.CARENCIA);
    }

    @Test
    void carenciaJaPublicadaNaoRepete() {
        assertThat(com(true, false, false, false).transicaoEm(FIM.plusSeconds(10))).isEqualTo(Transicao.NENHUMA);
    }

    @Test
    void depoisDe3DiasDeCarenciaPublicaOFim() {
        assertThat(novo().transicaoEm(FIM_CARENCIA)).isEqualTo(Transicao.FIM);
        assertThat(com(true, false, false, false).transicaoEm(FIM_CARENCIA)).isEqualTo(Transicao.FIM);
    }

    @Test
    void fimNaoPulaDireto() {
        assertThat(novo().transicaoEm(FIM_CARENCIA.plus(Duration.ofDays(30)))).isEqualTo(Transicao.FIM);
    }

    @Test
    void fimJaPublicadoNaoRepete() {
        assertThat(com(true, true, false, false).transicaoEm(FIM_CARENCIA.plusSeconds(1))).isEqualTo(Transicao.NENHUMA);
    }

    @Test
    void janelaDeAcessoVaiDoInicioAoFimDaCarencia() {
        var trial = novo();
        assertThat(trial.ativoEm(INICIO.minusSeconds(1))).isFalse();
        assertThat(trial.ativoEm(INICIO)).isTrue();
        assertThat(trial.ativoEm(FIM_CARENCIA.minusSeconds(1))).isTrue();
        assertThat(trial.ativoEm(FIM_CARENCIA)).isFalse();
        assertThat(trial.emCarenciaEm(FIM.minusSeconds(1))).isFalse();
        assertThat(trial.emCarenciaEm(FIM)).isTrue();
        assertThat(trial.emCarenciaEm(FIM_CARENCIA)).isFalse();
    }

    // HU-042: avisos de 3 e 1 dia

    private static Instant dias(int antesDoFim) {
        return FIM.minus(Duration.ofDays(antesDoFim));
    }

    @Test
    void avisaTresDiasAntesDoFim() {
        assertThat(novo().lembreteEm(null, dias(3), FUSO)).hasValue(3);
    }

    @Test
    void avisaUmDiaAntesDoFim() {
        assertThat(novo().lembreteEm(null, dias(1), FUSO)).hasValue(1);
    }

    @Test
    void naoAvisaEmOutrosDias() {
        for (int antes : new int[] {14, 5, 4, 2, 0}) {
            assertThat(novo().lembreteEm(null, dias(antes), FUSO)).as("%d dias antes", antes).isEmpty();
        }
    }

    @Test
    void naoRepeteAvisoJaPublicado() {
        assertThat(com(false, false, true, false).lembreteEm(null, dias(3), FUSO)).isEmpty();
        assertThat(com(false, false, false, true).lembreteEm(null, dias(1), FUSO)).isEmpty();
        assertThat(com(false, false, true, false).lembreteEm(null, dias(1), FUSO)).hasValue(1);
    }

    @Test
    void naoAvisaDepoisDoFim() {
        assertThat(novo().lembreteEm(null, FIM, FUSO)).isEmpty();
        assertThat(novo().lembreteEm(null, FIM.plus(Duration.ofDays(1)), FUSO)).isEmpty();
    }

    @Test
    void naoAvisaQuemJaAssinou() {
        assertThat(novo().lembreteEm(premiumAtiva(), dias(3), FUSO)).isEmpty();
        assertThat(novo().lembreteEm(premiumAtiva(), dias(1), FUSO)).isEmpty();
    }

    @Test
    void avisaQuemTemAssinaturaCancelada() {
        var cancelada = new Assinatura(Plano.PREMIUM, StatusAssinatura.CANCELADA, INICIO, null);
        assertThat(novo().lembreteEm(cancelada, dias(3), FUSO)).hasValue(3);
    }

    @Test
    void diasSaoContadosNoFusoDaFamilia() {
        // O fim cai em 15/11 no fuso de São Paulo. 02:30Z de 12/11 ainda é 11/11 local (4 dias); em UTC já seria 3.
        assertThat(novo().lembreteEm(null, Instant.parse("2026-11-12T02:30:00Z"), FUSO)).isEmpty();
        assertThat(novo().lembreteEm(null, Instant.parse("2026-11-12T03:30:00Z"), FUSO)).hasValue(3);
    }
}
