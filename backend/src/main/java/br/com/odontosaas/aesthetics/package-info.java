/**
 * Aplicações de harmonização com produto, lote, validade e regiões (rastreabilidade).
 */
@ApplicationModule(
        displayName = "Harmonização orofacial",
        allowedDependencies = {"audit", "clinic", "shared", "tenancy"})
package br.com.odontosaas.aesthetics;

import org.springframework.modulith.ApplicationModule;
