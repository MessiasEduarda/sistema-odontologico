/**
 * Infraestrutura compartilhada por todos os módulos: erros padronizados, rastreio de requisições e utilidades
 * web. Não contém regras de negócio.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN)
package br.com.odontosaas.shared;

import org.springframework.modulith.ApplicationModule;
