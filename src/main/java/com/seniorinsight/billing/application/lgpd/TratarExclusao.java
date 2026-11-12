package com.seniorinsight.billing.application.lgpd;

import java.util.UUID;


import com.seniorinsight.billing.application.porta.saida.ExclusaoDadosRepositorio;
import com.seniorinsight.billing.application.porta.saida.ExcluirCobrancaExterna;
import com.seniorinsight.billing.application.porta.saida.PublicadorEventos;
import com.seniorinsight.billing.domain.evento.ExclusaoConcluida;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TratarExclusao {

    private final ExclusaoDadosRepositorio repositorio;
    private final PublicadorEventos eventos;
    private final ExcluirCobrancaExterna cobrancaExterna;


    public TratarExclusao(ExclusaoDadosRepositorio repositorio, PublicadorEventos eventos,
                          ExcluirCobrancaExterna cobrancaExterna) {
        this.repositorio = repositorio;
        this.eventos = eventos;
        this.cobrancaExterna = cobrancaExterna;

    }

    @Transactional
    public void tratar(Pedido pedido) {
        if (!repositorio.reservar(pedido.protocolo())) {
            return;
        }
        if (!pedido.idoso()) {
            repositorio.bloquearCobranca(pedido.sub()).ifPresent(cobrancaExterna::excluir);
            repositorio.excluir(pedido.sub());
        }
        eventos.publicar(new ExclusaoConcluida(pedido.protocolo(), "billing-service"));
    }


    public record Pedido(UUID protocolo, UUID titular, String sub, boolean idoso) {
    }
}
