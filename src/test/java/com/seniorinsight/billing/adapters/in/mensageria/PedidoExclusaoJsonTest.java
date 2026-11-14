package com.seniorinsight.billing.adapters.in.mensageria;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seniorinsight.billing.application.lgpd.TratarExclusao;
import org.junit.jupiter.api.Test;

class PedidoExclusaoJsonTest {

    private final TratarExclusao tratador = mock(TratarExclusao.class);
    private final PedidoExclusaoJson leitor = new PedidoExclusaoJson(new ObjectMapper(), tratador);
    private final UUID id = UUID.randomUUID();
    private final UUID protocolo = UUID.randomUUID();

    @Test
    void rejeitaFonteInvalida() {
        assertThatThrownBy(() -> leitor.tratar(id, envelope("/identity-service", "lgpd.erasure.requested",
                protocolo.toString()))).isInstanceOf(PedidoExclusaoJson.PedidoInvalido.class);
    }

    @Test
    void rejeitaTipoInvalido() {
        assertThatThrownBy(() -> leitor.tratar(id, envelope("/report-service", "user.created",
                protocolo.toString()))).isInstanceOf(PedidoExclusaoJson.PedidoInvalido.class);
    }

    @Test
    void rejeitaSubjectDiferenteDoProtocolo() {
        assertThatThrownBy(() -> leitor.tratar(id, envelope("/report-service", "lgpd.erasure.requested",
                UUID.randomUUID().toString()))).isInstanceOf(PedidoExclusaoJson.PedidoInvalido.class);
    }

    private String envelope(String source, String type, String subject) {
        return """
                {
                  "specversion":"1.0",
                  "id":"%s",
                  "source":"%s",
                  "type":"%s",
                  "time":"2026-11-14T14:00:00Z",
                  "datacontenttype":"application/json",
                  "dataschema":"https://raw.githubusercontent.com/SeniorInsight-Project/si-contracts/main/events/lgpd.erasure.requested.v1.json",
                  "subject":"%s",
                  "data":{"protocolo":"%s","titularId":"%s","tipoTitular":"FAMILIAR","titularSub":"sub"}
                }
                """.formatted(id, source, type, subject, protocolo, UUID.randomUUID());
    }
}
