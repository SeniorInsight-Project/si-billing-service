package com.seniorinsight.billing.application.porta.saida;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.seniorinsight.billing.domain.assinatura.Plano;
import com.seniorinsight.billing.domain.assinatura.StatusAssinatura;

public interface TrialRepositorio {
    void bloquear(String sub);
    boolean excluido(String sub);
    Optional<Trial> buscar(String sub);
    Optional<Assinatura> assinatura(String sub);
    Optional<Trial> registrar(String sub, Instant inicio, Instant fim);
    List<String> mudancasPendentes(Instant agora, Instant fimCarenciaAte, String depoisDe);
    List<String> lembretesPendentes(Instant umInicio, Instant umFim, Instant tresInicio, Instant tresFim,
                                    String depoisDe);
    void marcarCarencia(UUID id, Instant agora);
    void marcarFim(UUID id, Instant agora);
    void marcarLembrete(UUID id, int dias, Instant agora);

    record Trial(UUID id, Instant inicio, Instant fim, boolean carenciaPublicada, boolean fimPublicado,
                 boolean avisoTresDias, boolean avisoUmDia) {
    }

    record Assinatura(Plano plano, StatusAssinatura status, Instant inicio, Instant fimPeriodo) {
    }
}
