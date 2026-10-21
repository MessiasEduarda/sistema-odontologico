/**
 * Produtos, movimentações e alertas de estoque e validade.
 */
@ApplicationModule(
        displayName = "Estoque",
        allowedDependencies = {"audit", "shared", "tenancy"})
package br.com.odontosaas.inventory;

import org.springframework.modulith.ApplicationModule;
