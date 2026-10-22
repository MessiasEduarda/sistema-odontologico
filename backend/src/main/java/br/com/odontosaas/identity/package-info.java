/**
 * Login, sessão, senha, convites, membros e papéis, termos de uso e e-mails de identidade.
 */
@ApplicationModule(
        displayName = "Identidade e acesso",
        allowedDependencies = {"audit", "shared", "tenancy", "tenancy :: entity", "tenancy :: repository"})
package br.com.odontosaas.identity;

import org.springframework.modulith.ApplicationModule;
