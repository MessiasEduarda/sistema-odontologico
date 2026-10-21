/**
 * Dados da clínica, profissionais (e agenda de atendimento), salas, procedimentos e logo; verificação de autoria clínica.
 */
@ApplicationModule(
        displayName = "Clínica",
        allowedDependencies = {"audit", "identity :: security", "shared", "tenancy"})
package br.com.odontosaas.clinic;

import org.springframework.modulith.ApplicationModule;
