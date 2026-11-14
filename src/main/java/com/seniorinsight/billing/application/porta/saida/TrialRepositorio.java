package com.seniorinsight.billing.application.porta.saida;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.seniorinsight.billing.domain.assinatura.Assinatura;
import com.seniorinsight.billing.domain.assinatura.Trial;

public interface TrialRepositorio {
    void bloquear(String sub);
    boolean excluido(String sub);
    Optional<Trial> buscar(String sub);
    Optional<Assinatura> assinatura(String sub);
    Optional<Trial> registrar(String sub, Instant inicio, Instant fim);
    List<String> mudancasPendentes(Instant agora, Instant fimCarenciaAte, String depoisDe);
    List<String> lembretesPendentes(Instant umInicio, Instant umFim, Instant tresInicio, Instant tresFim,
                                    String depoisDe);
    boolean marcarCarencia(UUID id, Instant agora);
    boolean marcarFim(UUID id, Instant agora);
    boolean marcarLembrete(UUID id, int dias, Instant agora);
}
