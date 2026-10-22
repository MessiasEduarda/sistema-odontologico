package br.com.odontosaas.inventory.dto.response;

import java.time.LocalDate;
import java.util.UUID;

public record ExpiringLot(UUID productId, String productName, String lotNumber, LocalDate expiryDate) {}
