package br.com.odontosaas.finance.dto.response;

import java.util.UUID;

public record SupplierView(
        UUID id, String name, String document, String phone, String email, String notes, boolean active) {}
