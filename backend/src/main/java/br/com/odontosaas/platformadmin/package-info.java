/**
 * Indicadores, clínicas (criação e situação), auditoria da plataforma e primeiro administrador.
 */
@ApplicationModule(
        displayName = "Administração da plataforma",
        allowedDependencies = {
            "audit",
            "identity",
            "identity :: config",
            "identity :: security",
            "shared",
            "subscription :: service",
            "tenancy",
            "tenancy :: entity",
            "tenancy :: repository"
        })
package br.com.odontosaas.platformadmin;

import org.springframework.modulith.ApplicationModule;
