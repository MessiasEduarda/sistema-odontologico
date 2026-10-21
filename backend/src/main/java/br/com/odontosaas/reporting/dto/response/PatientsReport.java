package br.com.odontosaas.reporting.dto.response;

import java.util.List;

public record PatientsReport(
        long active, long inactive, List<MonthCount> newByMonth, List<BirthdayRow> birthdaysThisMonth) {}
