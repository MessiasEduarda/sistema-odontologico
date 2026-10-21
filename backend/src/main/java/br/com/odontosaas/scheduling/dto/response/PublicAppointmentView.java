package br.com.odontosaas.scheduling.dto.response;

import java.time.Instant;

/** Visão pública da consulta (link enviado ao paciente). Sem dados clínicos. */
public record PublicAppointmentView(
        String clinicName,
        String patientFirstName,
        String professionalName,
        Instant startsAt,
        Instant endsAt,
        String status,
        boolean canRespond) {}
