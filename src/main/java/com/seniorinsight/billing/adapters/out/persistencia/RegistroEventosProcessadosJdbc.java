package com.seniorinsight.billing.adapters.out.persistencia;

import java.util.UUID;

import com.seniorinsight.billing.application.porta.saida.RegistroEventosProcessados;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Tabela {@code evento_processado}: o {@code ON CONFLICT DO NOTHING} torna o registro atômico entre réplicas. */
@Component
class RegistroEventosProcessadosJdbc implements RegistroEventosProcessados {

    private final JdbcTemplate jdbc;

    RegistroEventosProcessadosJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean registrarSeNovo(UUID eventoId, String tipoEvento) {
        return jdbc.update("""
                INSERT INTO evento_processado (id, tipo_evento)
                VALUES (?, ?)
                ON CONFLICT (id) DO NOTHING
                """, eventoId, tipoEvento) == 1;
    }
}
