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
            "finance :: dto",
            "finance :: service",
            "medicalrecord :: dto",
            "medicalrecord :: service",
            "patients :: event",
            "scheduling :: dto",
            "scheduling :: service",
            "shared",
            "tenancy",
            "treatment :: dto",
            "treatment :: service"
        })
package br.com.odontosaas.portal;

import org.springframework.modulith.ApplicationModule;
