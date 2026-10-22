package br.com.odontosaas.shared;

public final class Flags {

    private Flags() {}

    public static boolean activeIfOmitted(Boolean active) {
        return active == null || active;
    }
}
