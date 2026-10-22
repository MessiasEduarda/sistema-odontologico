package br.com.odontosaas.inventory.dto.response;

import java.util.List;

public record Alerts(List<ProductView> lowStock, List<ExpiringLot> expiring) {}
