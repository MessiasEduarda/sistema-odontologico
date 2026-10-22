/**
 * Mensagens por e-mail (confirmação, lembrete, orientações, links), modelos e rotinas de envio.
 */
@ApplicationModule(
        displayName = "Comunicação",
        allowedDependencies = {
            "audit",
            "consent :: event",
            "medicalrecord :: event",
            "scheduling :: event",
            "scheduling :: service",
            "shared",
            "tenancy"
        })
package br.com.odontosaas.communication;

import org.springframework.modulith.ApplicationModule;
