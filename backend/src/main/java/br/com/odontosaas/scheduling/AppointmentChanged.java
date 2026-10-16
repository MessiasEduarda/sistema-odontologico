package br.com.odontosaas.scheduling;

import java.util.UUID;

/**
 * Evento publicado (na mesma transação) quando um agendamento é criado, remarcado ou cancelado. Outros módulos
 * (ex.: Comunicação) reagem sem que a Agenda dependa deles.
 */
public record AppointmentChanged(UUID tenantId, UUID appointmentId, Kind kind) {

    public enum Kind {
        CREATED,
        RESCHEDULED,
        CANCELLED
    }
}
