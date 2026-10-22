/**
 * Planos, assinatura de cada clínica, limites, bloqueio por plano e por pagamento vencido.
 */
@ApplicationModule(
        displayName = "Planos e assinatura",
        allowedDependencies = {"audit", "shared", "tenancy"})
package br.com.odontosaas.subscription;

import org.springframework.modulith.ApplicationModule;
