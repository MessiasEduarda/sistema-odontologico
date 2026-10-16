package br.com.odontosaas.shared;

/** Controle de concorrência otimista para atualizações SQL com "where ... and version = ?". */
public final class Versioned {

    private Versioned() {}

    /**
     * @param updated linhas atualizadas
     * @param exists se o registro existe na clínica (para diferenciar 404 de 409)
     */
    public static void check(int updated, boolean exists, String label) {
        if (updated > 0) return;
        if (!exists) throw ApiException.notFound(label);
        throw ApiException.conflict(
                "STALE_VERSION", "O registro foi alterado por outra pessoa. Recarregue e tente novamente.");
    }

    public static void requireFound(int updated, String label) {
        if (updated == 0) throw ApiException.notFound(label);
    }
}
