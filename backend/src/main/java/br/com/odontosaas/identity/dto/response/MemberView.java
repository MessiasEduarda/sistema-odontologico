package br.com.odontosaas.identity.dto.response;

import java.util.Set;
import java.util.UUID;

public record MemberView(UUID membershipId, UUID userId, String name, String email, String status, Set<String> roles) {}
