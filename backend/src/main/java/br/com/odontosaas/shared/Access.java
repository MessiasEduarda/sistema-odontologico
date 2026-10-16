package br.com.odontosaas.shared;

/** Expressões de autorização reutilizadas nos controllers (@PreAuthorize). */
public final class Access {

    private Access() {}

    /** Qualquer membro autenticado com uma clínica ativa. */
    public static final String MEMBER = "isAuthenticated() and principal.tenantId() != null";
}
