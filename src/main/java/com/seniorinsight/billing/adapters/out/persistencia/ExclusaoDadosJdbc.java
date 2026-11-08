package com.seniorinsight.billing.adapters.out.persistencia;

import java.util.UUID;

import com.seniorinsight.billing.application.porta.saida.ExclusaoDadosRepositorio;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class ExclusaoDadosJdbc implements ExclusaoDadosRepositorio {

    private final JdbcTemplate jdbc;

    ExclusaoDadosJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean reservar(UUID protocolo) {
        return jdbc.update("INSERT INTO exclusao_confirmada (protocolo) VALUES (?) ON CONFLICT DO NOTHING",
                protocolo) == 1;
    }

    @Override
    public boolean assinaturaAtiva(String titularSub) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM assinatura
                    WHERE titular_sub = ? AND plano = 'PREMIUM' AND status IN ('ATIVA', 'INADIMPLENTE')
                )
                """, Boolean.class, titularSub));
    }

    @Override
    public void excluir(String titularSub) {
        bloquear(titularSub);
        if (assinaturaAtiva(titularSub)) {
            throw new IllegalStateException("Exclusão aguardando cancelamento da assinatura Premium");
        }
        if (Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM assinatura WHERE titular_sub = ?
                    AND (stripe_customer_id IS NOT NULL OR stripe_subscription_id IS NOT NULL)
                )
                """, Boolean.class, titularSub))) {
            throw new IllegalStateException("Exclusão aguardando tratamento da cobrança no Stripe");
        }

        jdbc.update("INSERT INTO titular_excluido (chave) VALUES (sha256(convert_to(?, 'UTF8'))) ON CONFLICT DO NOTHING",
                titularSub);
        jdbc.update("""
                DELETE FROM outbox_evento
                WHERE agregado_id IN (SELECT id FROM assinatura WHERE titular_sub = ?
                                      UNION ALL SELECT id FROM uso_trial WHERE titular_sub = ?)
                   OR payload->>'titularSub' = ? OR payload->'data'->>'titularSub' = ?
                """, titularSub, titularSub, titularSub, titularSub);
        jdbc.update("DELETE FROM assinatura WHERE titular_sub = ?", titularSub);
        jdbc.update("DELETE FROM uso_trial WHERE titular_sub = ?", titularSub);
    }

    private void bloquear(String chave) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(CAST(? AS text), 0))", rs -> null, chave);
    }
}
