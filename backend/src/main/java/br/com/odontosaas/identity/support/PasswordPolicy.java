package br.com.odontosaas.identity.support;

import br.com.odontosaas.shared.ApiException;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 128;

    private static final Set<String> COMMON = Set.of(
            "1234567890",
            "12345678910",
            "0123456789",
            "senha12345",
            "senha123456",
            "password123",
            "password1234",
            "qwertyuiop",
            "abcdefghij",
            "odonto12345",
            "dentista123",
            "clinica123",
            "aaaaaaaaaa",
            "mudar12345",
            "trocar1234");

    public void validate(String field, String password, String email) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw ApiException.invalidField(field, "A senha deve ter pelo menos " + MIN_LENGTH + " caracteres.");
        }
        if (password.length() > MAX_LENGTH) {
            throw ApiException.invalidField(field, "A senha deve ter no máximo " + MAX_LENGTH + " caracteres.");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (COMMON.contains(lower) || lower.chars().distinct().count() < 4) {
            throw ApiException.invalidField(field, "Essa senha é muito comum. Escolha outra.");
        }
        if (email != null && lower.equals(email.toLowerCase(Locale.ROOT))) {
            throw ApiException.invalidField(field, "A senha não pode ser igual ao e-mail.");
        }
    }
}
