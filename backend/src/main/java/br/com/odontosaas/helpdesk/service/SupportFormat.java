package br.com.odontosaas.helpdesk.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

final class SupportFormat {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm").withZone(ZoneId.of("America/Sao_Paulo"));

    private SupportFormat() {}

    static String dateTime(Instant instant) {
        return instant == null ? "-" : DATE_TIME.format(instant);
    }
}
