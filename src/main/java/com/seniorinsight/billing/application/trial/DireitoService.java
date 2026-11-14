package com.seniorinsight.billing.application.trial;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import com.seniorinsight.billing.application.porta.saida.PublicadorEventos;
import com.seniorinsight.billing.application.porta.saida.TrialRepositorio;
import com.seniorinsight.billing.domain.assinatura.AcessoAoPremium;
import com.seniorinsight.billing.domain.assinatura.AcessoPlano;
import com.seniorinsight.billing.domain.assinatura.Assinatura;
import com.seniorinsight.billing.domain.assinatura.Trial;
import com.seniorinsight.billing.domain.evento.DireitoAlterado;
import com.seniorinsight.billing.domain.evento.TrialPertoDoFim;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Orquestra persistência e eventos; as regras do direito ficam em {@code domain.assinatura}. */
@Service
public class DireitoService {
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
        if (!Trial.podeIniciar(anterior, assinatura, agora)) {
            return direito(anterior, assinatura, agora);
        }

        Trial criado = trials.registrar(sub, agora, Trial.fimPara(agora)).orElseThrow();
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
        if (trial == null) {
            return;
        }
        boolean marcou = switch (trial.transicaoEm(agora)) {
            case NENHUMA -> false;
            case CARENCIA -> trials.marcarCarencia(trial.id(), agora);
            case FIM -> trials.marcarFim(trial.id(), agora);
        };
        if (marcou) {
            publicarDireito(sub, trial, direito(trial, trials.assinatura(sub).orElse(null), agora));
        }
    }

    @Transactional
    public void lembrar(String sub, Instant agora) {
        trials.bloquear(sub);
        Trial trial = trials.buscar(sub).orElse(null);
        if (trial == null) {
            return;
        }
        var dias = trial.lembreteEm(trials.assinatura(sub).orElse(null), agora, fuso);
        if (dias.isPresent() && trials.marcarLembrete(trial.id(), dias.getAsInt(), agora)) {
            eventos.publicar(new TrialPertoDoFim(trial.id(), sub, trial.fim(), dias.getAsInt()));
        }
    }

    private Direito direito(Trial trial, Assinatura assinatura, Instant agora) {
        var acesso = AcessoAoPremium.em(trial, assinatura, agora);
        return new Direito(acesso.plano(), acesso.acessoPremium(), acesso.assinaturaAtiva(),
                acesso.fimTrial(), acesso.fimAcesso(), acesso.emCarencia(), precoCentavos, "BRL");
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
