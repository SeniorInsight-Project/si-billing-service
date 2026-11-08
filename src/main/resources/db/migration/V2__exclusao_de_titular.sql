
CREATE TABLE exclusao_confirmada (
    protocolo UUID PRIMARY KEY
);

-- A impressão é usada só para impedir que mensagens antigas restaurem os dados excluídos.
CREATE TABLE titular_excluido (
    chave BYTEA PRIMARY KEY
);

CREATE FUNCTION impedir_recriacao_titular_excluido() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    chave_titular text := TG_ARGV[1] || (to_jsonb(NEW) ->> TG_ARGV[0]);
BEGIN
    PERFORM pg_advisory_xact_lock(hashtextextended(chave_titular, 0));
    IF EXISTS (SELECT 1 FROM titular_excluido WHERE chave = sha256(convert_to(chave_titular, 'UTF8'))) THEN
        RAISE EXCEPTION 'Dados de titular excluído não podem ser recriados' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER impedir_recriacao_assinatura_titular_sub
    BEFORE INSERT OR UPDATE OF titular_sub ON assinatura
    FOR EACH ROW EXECUTE FUNCTION impedir_recriacao_titular_excluido('titular_sub', '');

CREATE TRIGGER impedir_recriacao_uso_trial_titular_sub
    BEFORE INSERT OR UPDATE OF titular_sub ON uso_trial
    FOR EACH ROW EXECUTE FUNCTION impedir_recriacao_titular_excluido('titular_sub', '');
