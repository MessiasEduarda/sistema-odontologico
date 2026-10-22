/**
 * Prontuário: anamnese, odontograma, evoluções, procedimentos realizados, documentos, anexos e orientações.
 */
@ApplicationModule(
        displayName = "Prontuário",
        allowedDependencies = {"audit", "clinic", "shared", "tenancy"})
package br.com.odontosaas.medicalrecord;

import org.springframework.modulith.ApplicationModule;
