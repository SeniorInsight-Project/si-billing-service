package com.seniorinsight.billing.application.lgpd;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.seniorinsight.billing.application.porta.saida.ExclusaoDadosRepositorio;
import com.seniorinsight.billing.application.porta.saida.ExcluirCobrancaExterna;
import com.seniorinsight.billing.application.porta.saida.PublicadorEventos;
import com.seniorinsight.billing.domain.evento.ExclusaoConcluida;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TratarExclusao {

    public static final String TIPO = "lgpd.erasure.requested";
    private static final String SCHEMA =
            "https://raw.githubusercontent.com/SeniorInsight-Project/si-contracts/main/events/lgpd.erasure.requested.v1.json";

    private final ObjectMapper json;
    private final ExclusaoDadosRepositorio repositorio;
    private final PublicadorEventos eventos;
    private final ExcluirCobrancaExterna cobrancaExterna;


    public TratarExclusao(ObjectMapper json, ExclusaoDadosRepositorio repositorio, PublicadorEventos eventos,
                          ExcluirCobrancaExterna cobrancaExterna) {
        this.json = json;
        this.repositorio = repositorio;
        this.eventos = eventos;
        this.cobrancaExterna = cobrancaExterna;

    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void tratar(UUID mensagemId, String corpo) {
        JsonNode envelope;
        try {
            envelope = json.readTree(corpo);
        } catch (JsonProcessingException e) {
            throw invalido();
        }
        if (envelope == null || !envelope.isObject() || !"1.0".equals(envelope.path("specversion").asText())
                || !"/report-service".equals(envelope.path("source").asText())
                || !TIPO.equals(envelope.path("type").asText())
                || !SCHEMA.equals(envelope.path("dataschema").asText())
                || !"application/json".equals(envelope.path("datacontenttype").asText())
                || !mensagemId.equals(uuid(envelope.path("id")))) {
            throw invalido();
        }
        try {
            Instant.parse(envelope.path("time").asText());
        } catch (DateTimeParseException e) {
            throw invalido();
        }
        JsonNode data = envelope.path("data");
        UUID protocolo = uuid(data.path("protocolo"));
        UUID titular = uuid(data.path("titularId"));
        String tipo = data.path("tipoTitular").asText();
        String sub = data.path("titularSub").textValue();
        if (!protocolo.equals(uuid(envelope.path("subject"))) || sub == null || sub.isBlank() || sub.length() > 255
                || (!"IDOSO".equals(tipo) && !"FAMILIAR".equals(tipo))) {
            throw invalido();
        }

        if (!repositorio.reservar(protocolo)) {
            return;
        }
        if ("FAMILIAR".equals(tipo)) {
            repositorio.bloquearCobranca(sub).ifPresent(cobrancaExterna::excluir);
            repositorio.excluir(sub);
        }
        eventos.publicar(new ExclusaoConcluida(protocolo, "billing-service"));
    }

    private static UUID uuid(JsonNode campo) {
        if (!campo.isTextual()) {
            throw invalido();
        }
        try {
            String texto = campo.textValue();
            UUID valor = UUID.fromString(texto);
            if (valor.toString().equalsIgnoreCase(texto)) {
                return valor;
            }
        } catch (IllegalArgumentException e) {
            throw invalido();
        }
        throw invalido();
    }

    private static IllegalArgumentException invalido() {
        return new IllegalArgumentException("Pedido de exclusão inválido");
    }
}
