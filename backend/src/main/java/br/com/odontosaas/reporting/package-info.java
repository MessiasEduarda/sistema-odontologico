/**
 * Painel inicial e relatórios (agenda, produção, financeiro, pacientes) com exportação CSV.
 */
@ApplicationModule(
        displayName = "Relatórios e painel",
        allowedDependencies = {"audit", "identity :: security", "shared", "tenancy"})
package br.com.odontosaas.reporting;

import org.springframework.modulith.ApplicationModule;
