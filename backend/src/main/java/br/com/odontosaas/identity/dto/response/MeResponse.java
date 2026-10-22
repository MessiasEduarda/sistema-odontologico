package br.com.odontosaas.identity.dto.response;

import java.util.List;

public record MeResponse(
        UserView user,
        boolean platformAdmin,
        ClinicSummary activeClinic,
        List<String> permissions,
        List<ClinicSummary> clinics,
        boolean termsAccepted,
        String termsVersion) {}
