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
import com.seniorinsight.billing.domain.assinatura.AcessoAoPremium;
import com.seniorinsight.billing.domain.evento.DireitoAlterado;
import com.seniorinsight.billing.domain.evento.TrialPertoDoFim;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DireitoService {
    private static final Duration TESTE = Duration.ofDays(14);
    public static final Duration CARENCIA = AcessoAoPremium.CARENCIA;

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
        boolean marcou = expirou ? trials.marcarFim(trial.id(), agora) : trials.marcarCarencia(trial.id(), agora);
        if (marcou) {
            publicarDireito(sub, trial, direito(trial, trials.assinatura(sub).orElse(null), agora));
        }
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
            if (trials.marcarLembrete(trial.id(), (int) dias, agora)) {
                eventos.publicar(new TrialPertoDoFim(trial.id(), sub, trial.fim(), (int) dias));
            }
        }
    }

    private Direito direito(Trial trial, Assinatura assinatura, Instant agora) {
        var acesso = AcessoAoPremium.calcular(trial == null ? null : trial.inicio(), trial == null ? null : trial.fim(),
                assinatura == null ? null : assinatura.plano(), assinatura == null ? null : assinatura.status(),
                assinatura == null ? null : assinatura.inicio(), assinatura == null ? null : assinatura.fimPeriodo(), agora);
        return new Direito(acesso.plano(), acesso.plano() != AcessoPlano.FREE, acesso.assinaturaAtiva(),
                acesso.fimTrial(), acesso.fimAcesso(), acesso.emCarencia(), precoCentavos, "BRL");
    }

    private static boolean premiumVigente(Assinatura assinatura, Instant agora) {
        return assinatura != null && AcessoAoPremium.premiumVigente(assinatura.plano(), assinatura.status(),
                assinatura.inicio(), assinatura.fimPeriodo(), agora);
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
