package com.seniorinsight.billing.domain.assinatura;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.OptionalInt;
import java.util.UUID;

/** Teste gratuito de uma família: 14 dias, uma única vez, mais 3 dias de carência (RN-005, RN-013). */
public record Trial(UUID id, Instant inicio, Instant fim, boolean carenciaPublicada, boolean fimPublicado,
                    boolean avisoTresDias, boolean avisoUmDia) {
    public static final Duration DURACAO = Duration.ofDays(14);
    public static final Duration CARENCIA = Duration.ofDays(3);

    /** O que o ciclo diário deve publicar para o trial. */
    public enum Transicao {
        NENHUMA, CARENCIA, FIM
    }

    public static Instant fimPara(Instant inicio) {
        return inicio.plus(DURACAO);
    }

    /** Só inicia quem nunca teve trial e não tem Premium vigente. */
    public static boolean podeIniciar(Trial anterior, Assinatura assinatura, Instant agora) {
        return anterior == null && (assinatura == null || !assinatura.vigenteEm(agora));
    }

    public Instant fimDaCarencia() {
        return fim.plus(CARENCIA);
    }

    public boolean ativoEm(Instant agora) {
        return !agora.isBefore(inicio) && agora.isBefore(fimDaCarencia());
    }

    public boolean emCarenciaEm(Instant agora) {
        return ativoEm(agora) && !agora.isBefore(fim);
    }

    public Transicao transicaoEm(Instant agora) {
        if (fimPublicado || agora.isBefore(fim)) {
            return Transicao.NENHUMA;
        }
        if (!agora.isBefore(fimDaCarencia())) {
            return Transicao.FIM;
        }
        return carenciaPublicada ? Transicao.NENHUMA : Transicao.CARENCIA;
    }

    /** Dias (3 ou 1) a avisar agora; vazio se não há aviso pendente ou se a família já assinou. */
    public OptionalInt lembreteEm(Assinatura assinatura, Instant agora, ZoneId fuso) {
        if (!agora.isBefore(fim) || (assinatura != null && assinatura.vigenteEm(agora))) {
            return OptionalInt.empty();
        }
        long dias = ChronoUnit.DAYS.between(agora.atZone(fuso).toLocalDate(), fim.atZone(fuso).toLocalDate());
        if ((dias == 3 && !avisoTresDias) || (dias == 1 && !avisoUmDia)) {
            return OptionalInt.of((int) dias);
        }
        return OptionalInt.empty();
    }
}
