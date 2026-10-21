package br.com.odontosaas.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configurações gerais usadas pelo módulo de identidade ({@code app.*}). */
@ConfigurationProperties(prefix = "app")
public record AppProperties(String publicUrl, Mail mail, Bootstrap bootstrap, Seed seed) {

    public record Mail(String from) {}

    public record Bootstrap(String platformAdminEmail, String platformAdminPassword) {}

    public record Seed(boolean enabled, String password) {}
}
