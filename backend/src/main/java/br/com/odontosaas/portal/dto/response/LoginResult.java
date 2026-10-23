package br.com.odontosaas.portal.dto.response;

import java.util.UUID;

public record LoginResult(String status, UUID challengeId, String maskedEmail, PortalMe me) {

    public static LoginResult authenticated(PortalMe me) {
        return new LoginResult("AUTHENTICATED", null, null, me);
    }

    public static LoginResult codeRequired(UUID challengeId, String maskedEmail) {
        return new LoginResult("CODE_REQUIRED", challengeId, maskedEmail, null);
    }
}
