/**
 * Acesso do paciente por link: consultas, termos e contato com a clínica.
 */
@ApplicationModule(
        displayName = "Portal do paciente",
        allowedDependencies = {
            "audit",
            "communication :: service",
            "consent :: dto",
            "consent :: service",
            "scheduling :: dto",
            "scheduling :: service",
            "shared",
            "tenancy"
        })
package br.com.odontosaas.portal;

import org.springframework.modulith.ApplicationModule;
