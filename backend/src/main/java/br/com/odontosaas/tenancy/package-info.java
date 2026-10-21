/**
 * Clínica (tenant), contexto da clínica na requisição e isolamento por RLS no banco.
 */
@ApplicationModule(
        displayName = "Clínicas (multi-tenant)",
        allowedDependencies = {"shared"})
package br.com.odontosaas.tenancy;

import org.springframework.modulith.ApplicationModule;
