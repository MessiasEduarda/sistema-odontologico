package br.com.odontosaas.shared;

public final class Versioned {

    private Versioned() {}

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
