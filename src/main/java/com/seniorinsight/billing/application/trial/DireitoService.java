package com.seniorinsight.billing.application.trial;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import com.seniorinsight.billing.application.porta.saida.PublicadorEventos;
import com.seniorinsight.billing.application.porta.saida.TrialRepositorio;
import com.seniorinsight.billing.application.porta.saida.TrialRepositorio.Assinatura;
import com.seniorinsight.billing.application.porta.saida.TrialRepositorio.Trial;
import com.seniorinsight.billing.domain.assinatura.AcessoPlano;
import com.seniorinsight.billing.domain.assinatura.Plano;
import com.seniorinsight.billing.domain.assinatura.StatusAssinatura;
import com.seniorinsight.billing.domain.evento.DireitoAlterado;
import com.seniorinsight.billing.domain.evento.TrialPertoDoFim;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DireitoService {
    private static final Duration TESTE = Duration.ofDays(14);
    public static final Duration CARENCIA = Duration.ofDays(3);

    private final TrialRepositorio trials;
    private final PublicadorEventos eventos;
    private final Long precoCentavos;
    private final ZoneId fuso;

    public DireitoService(TrialRepositorio trials, PublicadorEventos eventos,
            @Value("${billing.trial.preco-premium-centavos}") String preco,
            @Value("${billing.trial.fuso}") String fuso) {
        this.trials = trials;
        this.eventos = eventos;
        this.precoCentavos = preco.isBlank() ? null : Long.valueOf(preco);
        if (precoCentavos != null && precoCentavos <= 0) {
            throw new IllegalArgumentException("O preço mensal do Premium deve ser positivo");
        }
        this.fuso = ZoneId.of(fuso);
    }

    @Transactional
    public Direito iniciar(String sub, Instant agora) {
        agora = agora.truncatedTo(ChronoUnit.MICROS);
        if (sub.isBlank() || sub.length() > 255) {
            throw new IllegalArgumentException("Identificador de família inválido");
        }
        trials.bloquear(sub);
        if (trials.excluido(sub)) {
            throw new FamiliaExcluida();
        }

        Assinatura assinatura = trials.assinatura(sub).orElse(null);
        Trial anterior = trials.buscar(sub).orElse(null);
        if (anterior != null || premiumVigente(assinatura, agora)) {
            return direito(anterior, assinatura, agora);
        }

        Trial criado = trials.registrar(sub, agora, agora.plus(TESTE)).orElseThrow();
        Direito direito = direito(criado, assinatura, agora);
        publicarDireito(sub, criado, direito);
        return direito;
    }

    @Transactional
    public Direito consultar(String sub, Instant agora) {
        trials.bloquear(sub);
        return direito(trials.buscar(sub).orElse(null), trials.assinatura(sub).orElse(null), agora);
    }

    @Transactional
    public void atualizarCiclo(String sub, Instant agora) {
        trials.bloquear(sub);
        Trial trial = trials.buscar(sub).orElse(null);
        if (trial == null || trial.fimPublicado() || agora.isBefore(trial.fim())) {
            return;
        }
        boolean expirou = !agora.isBefore(trial.fim().plus(CARENCIA));
        if (!expirou && trial.carenciaPublicada()) {
            return;
        }
        if (expirou) {
            trials.marcarFim(trial.id(), agora);
        } else {
            trials.marcarCarencia(trial.id(), agora);
        }
        publicarDireito(sub, trial, direito(trial, trials.assinatura(sub).orElse(null), agora));
    }

    @Transactional
    public void lembrar(String sub, Instant agora) {
        trials.bloquear(sub);
        Trial trial = trials.buscar(sub).orElse(null);
        if (trial == null || !agora.isBefore(trial.fim())
                || premiumVigente(trials.assinatura(sub).orElse(null), agora)) {
            return;
        }
        long dias = ChronoUnit.DAYS.between(agora.atZone(fuso).toLocalDate(), trial.fim().atZone(fuso).toLocalDate());
        if ((dias == 3 && !trial.avisoTresDias()) || (dias == 1 && !trial.avisoUmDia())) {
            trials.marcarLembrete(trial.id(), (int) dias, agora);
            eventos.publicar(new TrialPertoDoFim(trial.id(), sub, trial.fim(), (int) dias));
        }
    }

    private Direito direito(Trial trial, Assinatura assinatura, Instant agora) {
        Instant fim = trial == null ? null : trial.fim();
        Instant fimAcessoTrial = fim == null ? null : fim.plus(CARENCIA);
        boolean assinaturaAtiva = assinatura != null && assinatura.plano() == Plano.PREMIUM
                && (assinatura.status() == StatusAssinatura.ATIVA || assinatura.status() == StatusAssinatura.INADIMPLENTE);

        if (premiumVigente(assinatura, agora)) {
            return new Direito(AcessoPlano.PREMIUM, true, assinaturaAtiva, fim, fimAcessoPago(assinatura),
                    assinatura.status() == StatusAssinatura.INADIMPLENTE, precoCentavos, "BRL");
        }
        if (trial != null && !agora.isBefore(trial.inicio()) && agora.isBefore(fimAcessoTrial)) {
            return new Direito(AcessoPlano.TRIAL, true, assinaturaAtiva, fim, fimAcessoTrial,
                    !agora.isBefore(fim), precoCentavos, "BRL");
        }
        return new Direito(AcessoPlano.FREE, false, assinaturaAtiva, fim, null, false, precoCentavos, "BRL");
    }

    private static boolean premiumVigente(Assinatura assinatura, Instant agora) {
        if (assinatura == null || assinatura.plano() == Plano.TRIAL || agora.isBefore(assinatura.inicio())) {
            return false;
        }
        Instant fim = fimAcessoPago(assinatura);
        return switch (assinatura.status()) {
            case ATIVA -> fim == null || agora.isBefore(fim);
            case INADIMPLENTE -> fim != null && agora.isBefore(fim);
            default -> false;
        };
    }

    private static Instant fimAcessoPago(Assinatura assinatura) {
        Instant fim = assinatura.fimPeriodo();
        return fim != null && assinatura.status() == StatusAssinatura.INADIMPLENTE ? fim.plus(CARENCIA) : fim;
    }

    private void publicarDireito(String sub, Trial trial, Direito direito) {
        eventos.publicar(new DireitoAlterado(trial.id(), sub, direito.plano(), direito.acessoPremium(),
                direito.acessoAte(), direito.fimTrialEm(), direito.emCarencia()));
    }

    public record Direito(AcessoPlano plano, boolean acessoPremium, boolean assinaturaAtiva,
                          Instant fimTrialEm, Instant acessoAte, boolean emCarencia,
                          Long precoPremiumCentavos, String moeda) {
    }

    public static class FamiliaExcluida extends RuntimeException {
    }
}
