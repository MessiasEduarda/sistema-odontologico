package br.com.odontosaas.scheduling.event;

import java.util.UUID;

public record AppointmentChanged(UUID tenantId, UUID appointmentId, Kind kind) {

    public enum Kind {
        CREATED,
        RESCHEDULED,
        CANCELLED
    }
}
