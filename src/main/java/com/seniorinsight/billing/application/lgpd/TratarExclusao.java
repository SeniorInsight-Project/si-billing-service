package com.seniorinsight.billing.application.lgpd;

import java.util.UUID;

import com.seniorinsight.billing.application.porta.saida.ExclusaoDadosRepositorio;
import com.seniorinsight.billing.application.porta.saida.ExcluirCobrancaExterna;
import com.seniorinsight.billing.application.porta.saida.PublicadorEventos;
import com.seniorinsight.billing.domain.evento.ExclusaoConcluida;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class TratarExclusao {

    private final ExclusaoDadosRepositorio repositorio;
    private final PublicadorEventos eventos;
    private final ExcluirCobrancaExterna cobrancaExterna;
    private final TransactionTemplate transacao;

    public TratarExclusao(ExclusaoDadosRepositorio repositorio, PublicadorEventos eventos,
                          ExcluirCobrancaExterna cobrancaExterna, TransactionTemplate transacao) {
        this.repositorio = repositorio;
        this.eventos = eventos;
        this.cobrancaExterna = cobrancaExterna;
        this.transacao = transacao;
    }

    public void tratar(Pedido pedido) {
        Preparacao preparacao = transacao.execute(status -> {
            if (repositorio.reservar(pedido.protocolo(), pedido.sub(), pedido.idoso())
                    == ExclusaoDadosRepositorio.EstadoReserva.CONCLUIDA) {
                return Preparacao.CONCLUIDA;
            }
            if (pedido.idoso()) {
                return Preparacao.SOMENTE_CONFIRMACAO;
            }
            try {
                return repositorio.bloquearCobranca(pedido.sub())
                        .map(Preparacao::comReferencias)
                        .orElse(Preparacao.SOMENTE_LOCAL);
            } catch (IllegalStateException e) {
                if (e.getMessage() != null && e.getMessage().contains("aguardando cancelamento")) {
                    return Preparacao.AGUARDA_PREMIUM;
                }
                throw e;
            }
        });
        if (preparacao == Preparacao.CONCLUIDA) {
            return;
        }
        if (preparacao.aguardaPremium()) {
            throw new ExclusaoPendenteException("Exclusão aguardando cancelamento da assinatura Premium");
        }
        if (preparacao.referencias() != null) {
            cobrancaExterna.excluir(preparacao.referencias());
        }
        transacao.executeWithoutResult(status -> {
            if (!pedido.idoso()) {
                repositorio.excluir(pedido.sub());
            }
            eventos.publicar(new ExclusaoConcluida(pedido.protocolo(), "billing-service"));
            repositorio.concluir(pedido.protocolo());
        });
    }

    public static class ExclusaoPendenteException extends RuntimeException {
        public ExclusaoPendenteException(String message) {
            super(message);
        }
    }

    private record Preparacao(ExclusaoDadosRepositorio.ReferenciasStripe referencias, boolean aguardaPremium) {
        private static final Preparacao CONCLUIDA = new Preparacao(null, false);
        private static final Preparacao SOMENTE_CONFIRMACAO = new Preparacao(null, false);
        private static final Preparacao SOMENTE_LOCAL = new Preparacao(null, false);
        private static final Preparacao AGUARDA_PREMIUM = new Preparacao(null, true);

        private static Preparacao comReferencias(ExclusaoDadosRepositorio.ReferenciasStripe referencias) {
            return new Preparacao(referencias, false);
        }
    }


    public record Pedido(UUID protocolo, UUID titular, String sub, boolean idoso) {
    }
}
