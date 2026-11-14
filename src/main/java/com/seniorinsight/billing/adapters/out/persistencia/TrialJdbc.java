package com.seniorinsight.billing.adapters.out.persistencia;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.seniorinsight.billing.application.porta.saida.TrialRepositorio;
import com.seniorinsight.billing.domain.assinatura.Plano;
import com.seniorinsight.billing.domain.assinatura.StatusAssinatura;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class TrialJdbc implements TrialRepositorio {
    private final JdbcTemplate jdbc;

    TrialJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void bloquear(String sub) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(CAST(? AS text), 0))", rs -> null, sub);
    }

    @Override
    public boolean excluido(String sub) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM titular_excluido WHERE chave = sha256(convert_to(?, 'UTF8')))
                """, Boolean.class, sub));
    }

    @Override
    public Optional<Trial> buscar(String sub) {
        return jdbc.query("""
                SELECT id, iniciado_em, expira_em, carencia_publicada_em, fim_publicado_em,
                       aviso_tres_dias_em, aviso_um_dia_em
                  FROM uso_trial WHERE titular_sub = ?
                """, (rs, linha) -> new Trial(rs.getObject(1, UUID.class),
                rs.getObject(2, OffsetDateTime.class).toInstant(), rs.getObject(3, OffsetDateTime.class).toInstant(),
                rs.getObject(4) != null, rs.getObject(5) != null, rs.getObject(6) != null, rs.getObject(7) != null),
                sub).stream().findFirst();
    }

    @Override
    public Optional<Assinatura> assinatura(String sub) {
        return jdbc.query("""
                SELECT plano, status, inicio_em, fim_periodo_em FROM assinatura WHERE titular_sub = ?
                """, (rs, linha) -> {
                    var fim = rs.getObject(4, OffsetDateTime.class);
                    return new Assinatura(Plano.valueOf(rs.getString(1)), StatusAssinatura.valueOf(rs.getString(2)),
                            rs.getObject(3, OffsetDateTime.class).toInstant(), fim == null ? null : fim.toInstant());
                }, sub).stream().findFirst();
    }

    @Override
    public Optional<Trial> registrar(String sub, Instant inicio, Instant fim) {
        return jdbc.query("""
                INSERT INTO uso_trial (titular_sub, iniciado_em, expira_em) VALUES (?, ?, ?)
                ON CONFLICT (titular_sub) DO NOTHING
                RETURNING id, iniciado_em, expira_em
                """, (rs, linha) -> new Trial(rs.getObject(1, UUID.class),
                rs.getObject(2, OffsetDateTime.class).toInstant(), rs.getObject(3, OffsetDateTime.class).toInstant(),
                false, false, false, false),
                sub, data(inicio), data(fim)).stream().findFirst();
    }

    @Override
    public List<String> mudancasPendentes(Instant agora, Instant fimCarenciaAte, String depoisDe) {
        return jdbc.queryForList("""
                SELECT titular_sub FROM uso_trial
                 WHERE titular_sub > ? AND fim_publicado_em IS NULL
                   AND ((carencia_publicada_em IS NULL AND expira_em <= ?) OR expira_em <= ?)
                 ORDER BY titular_sub LIMIT 200
                """, String.class, depoisDe, data(agora), data(fimCarenciaAte));
    }

    @Override
    public List<String> lembretesPendentes(Instant umInicio, Instant umFim, Instant tresInicio, Instant tresFim,
                                           String depoisDe) {
        return jdbc.queryForList("""
                SELECT titular_sub FROM uso_trial WHERE titular_sub > ? AND fim_publicado_em IS NULL
                 AND ((aviso_um_dia_em IS NULL AND expira_em >= ? AND expira_em < ?)
                   OR (aviso_tres_dias_em IS NULL AND expira_em >= ? AND expira_em < ?))
                 ORDER BY titular_sub LIMIT 200
                """, String.class, depoisDe, data(umInicio), data(umFim), data(tresInicio), data(tresFim));
    }

    @Override
    public boolean marcarCarencia(UUID id, Instant agora) {
        return jdbc.update("UPDATE uso_trial SET carencia_publicada_em = ? WHERE id = ? AND carencia_publicada_em IS NULL",
                data(agora), id) == 1;
    }

    @Override
    public boolean marcarFim(UUID id, Instant agora) {
        return jdbc.update("UPDATE uso_trial SET fim_publicado_em = ? WHERE id = ? AND fim_publicado_em IS NULL",
                data(agora), id) == 1;
    }

    @Override
    public boolean marcarLembrete(UUID id, int dias, Instant agora) {
        String comando = switch (dias) {
            case 1 -> "UPDATE uso_trial SET aviso_um_dia_em = ? WHERE id = ? AND aviso_um_dia_em IS NULL";
            case 3 -> "UPDATE uso_trial SET aviso_tres_dias_em = ? WHERE id = ? AND aviso_tres_dias_em IS NULL";
            default -> throw new IllegalArgumentException("Prazo de lembrete inválido");
        };
        return jdbc.update(comando, data(agora), id) == 1;
    }

    private static OffsetDateTime data(Instant instante) {
        return instante.atOffset(ZoneOffset.UTC);
    }
}
