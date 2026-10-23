package br.com.odontosaas.shared;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void checksLengthUppercaseAndSpecialCharacterInThisOrder() {
        assertThatThrownBy(() -> policy.validate("password", "ab", null))
                .satisfies(e -> assertMessage(e, "A senha deve ter no mínimo 8 caracteres."));
        assertThatThrownBy(() -> policy.validate("password", "abcdefgh", null))
                .satisfies(e -> assertMessage(e, "A senha deve ter pelo menos uma letra maiúscula."));
        assertThatThrownBy(() -> policy.validate("password", "Abcdefgh", null))
                .satisfies(
                        e -> assertMessage(e, "A senha deve ter pelo menos um caractere especial (ex.: ! @ # $ %)."));
        assertThatCode(() -> policy.validate("password", "Abcdefg!", null)).doesNotThrowAnyException();
    }

    private static void assertMessage(Throwable e, String message) {
        org.assertj.core.api.Assertions.assertThat(
                        ((ApiException) e).fieldErrors().getFirst().get("message"))
                .isEqualTo(message);
    }
}
