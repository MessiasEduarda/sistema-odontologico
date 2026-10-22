package br.com.odontosaas.patients.support;

import br.com.odontosaas.shared.ApiException;

public final class Cpf {

    private Cpf() {}

    public static String digits(String value) {
        return value == null ? null : value.replaceAll("[^0-9]", "");
    }

    public static boolean isValid(String value) {
        String d = digits(value);
        if (d == null || d.length() != 11 || d.chars().distinct().count() == 1) return false;
        return check(d, 9) == d.charAt(9) - '0' && check(d, 10) == d.charAt(10) - '0';
    }

    private static int check(String d, int len) {
        int sum = 0;
        for (int i = 0; i < len; i++) sum += (d.charAt(i) - '0') * (len + 1 - i);
        int rest = (sum * 10) % 11;
        return rest == 10 ? 0 : rest;
    }

    public static String normalizeOrReject(String value, String field) {
        if (value == null || value.isBlank()) return null;
        if (!isValid(value)) throw ApiException.invalidField(field, "CPF inválido.");
        return digits(value);
    }
}
