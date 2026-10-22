/**
 * Orçamentos e planos de tratamento (rascunho, apresentado, aprovado).
 */
@ApplicationModule(
        displayName = "Orçamentos",
        allowedDependencies = {"audit", "medicalrecord :: event", "shared", "tenancy"})
package br.com.odontosaas.treatment;

import org.springframework.modulith.ApplicationModule;
