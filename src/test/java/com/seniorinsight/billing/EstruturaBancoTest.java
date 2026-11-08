package com.seniorinsight.billing;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** O contexto subir já prova que o Hibernate validou o mapeamento ({@code ddl-auto: validate}) contra a V1. */
@TesteIntegracao
class EstruturaBancoTest {

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void flywayAplicaAsMigracoesNoSchemaBilling() {
        assertThat(flyway.info().applied()).extracting(MigrationInfo::getVersion)
                .contains(MigrationVersion.fromVersion("1"), MigrationVersion.fromVersion("2"));
        assertThat(jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'billing'", String.class))
                .contains("assinatura", "uso_trial", "outbox_evento", "evento_processado", "flyway_schema_history");
    }

    @Test
    void outboxTemIndiceParcialDosPendentes() {
        String definicao = jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = 'billing' AND indexname = ?",
                String.class, "ix_outbox_evento_pendentes");

        assertThat(definicao).contains("(criado_em)").contains("WHERE (publicado_em IS NULL)");
    }
}
