package br.com.odontosaas.reporting.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record Dashboard(
        Long appointmentsToday,
        Long confirmedToday,
        Long completedToday,
        Long activePatients,
        Long newPatientsThisMonth,
        BigDecimal receivedThisMonth,
        BigDecimal overdueAmount,
        Long lowStockProducts,
        List<TodayAppointment> today,
        List<DashboardAlert> alerts) {}
