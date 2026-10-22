/**
 * Consultas, disponibilidade, bloqueios de agenda, lista de espera e links do paciente.
 */
@ApplicationModule(
        displayName = "Agenda",
        allowedDependencies = {"audit", "shared", "tenancy"})
package br.com.odontosaas.scheduling;

import org.springframework.modulith.ApplicationModule;
