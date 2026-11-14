ALTER TABLE uso_trial
    ADD COLUMN carencia_publicada_em TIMESTAMPTZ,
    ADD COLUMN fim_publicado_em TIMESTAMPTZ,
    ADD COLUMN aviso_tres_dias_em TIMESTAMPTZ,
    ADD COLUMN aviso_um_dia_em TIMESTAMPTZ;

CREATE INDEX ix_uso_trial_pendente ON uso_trial (expira_em) WHERE fim_publicado_em IS NULL;
