package br.com.odontosaas.scheduling.dto.response;

import java.util.List;

public record AppointmentDetail(AppointmentView appointment, List<AppointmentEventView> history) {}
