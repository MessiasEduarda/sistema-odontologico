/**
 * Recebíveis, parcelas, pagamentos, estornos, contas a pagar, fornecedores e fluxo de caixa.
 */
@ApplicationModule(
        displayName = "Financeiro",
        allowedDependencies = {"audit", "shared", "tenancy", "treatment :: event"})
package br.com.odontosaas.finance;

import org.springframework.modulith.ApplicationModule;
