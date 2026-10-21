package br.com.odontosaas.shared;

/**
 * Campos booleanos opcionais de cadastros. Um {@code boolean} primitivo omitido na requisição viraria {@code false} em
 * silêncio (desativando o registro ou revogando um consentimento); por isso esses campos são {@link Boolean}: omitido
 * em cadastro novo usa o padrão, omitido em edição mantém o valor atual (SQL {@code coalesce(?, coluna)}).
 */
public final class Flags {

    private Flags() {}

    /** Cadastro novo com o campo "ativo" omitido: nasce ativo. */
    public static boolean activeIfOmitted(Boolean active) {
        return active == null || active;
    }
}
