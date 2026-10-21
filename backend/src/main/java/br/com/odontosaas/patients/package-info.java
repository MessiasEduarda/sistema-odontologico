/**
 * Cadastro, importação em massa e exportação LGPD de pacientes.
 */
@ApplicationModule(
        displayName = "Pacientes",
        allowedDependencies = {"audit", "shared", "tenancy"})
package br.com.odontosaas.patients;

import org.springframework.modulith.ApplicationModule;
