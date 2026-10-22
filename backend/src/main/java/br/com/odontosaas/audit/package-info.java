/**
 * Trilha de auditoria imutável das ações da clínica e da plataforma; consulta dos eventos da clínica.
 */
@ApplicationModule(
        displayName = "Auditoria",
        allowedDependencies = {"shared", "tenancy"})
package br.com.odontosaas.audit;

import org.springframework.modulith.ApplicationModule;
