/**
 * Central de suporte: chamados da clínica para a equipe da plataforma, conversa, anexos, prazos e avisos por e-mail.
 */
@ApplicationModule(
        displayName = "Suporte",
        allowedDependencies = {"audit", "shared", "tenancy"})
package br.com.odontosaas.helpdesk;

import org.springframework.modulith.ApplicationModule;
