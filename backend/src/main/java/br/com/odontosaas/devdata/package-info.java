/**
 * Massa de dados fictícia de desenvolvimento. Existe somente no perfil dev.
 */
@ApplicationModule(
        displayName = "Dados de desenvolvimento",
        allowedDependencies = {
            "identity",
            "identity :: config",
            "shared",
            "subscription :: service",
            "tenancy",
            "tenancy :: entity",
            "tenancy :: repository"
        })
package br.com.odontosaas.devdata;

import org.springframework.modulith.ApplicationModule;
