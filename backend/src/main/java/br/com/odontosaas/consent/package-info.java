/**
 * Modelos e termos de consentimento, assinatura presencial e por link público.
 */
@ApplicationModule(
        displayName = "Termos de consentimento",
        allowedDependencies = {"audit", "shared", "tenancy"})
package br.com.odontosaas.consent;

import org.springframework.modulith.ApplicationModule;
