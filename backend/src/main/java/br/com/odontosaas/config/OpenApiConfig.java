package br.com.odontosaas.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI odontoOpenApi(@Value("${spring.application.name}") String appName) {
        return new OpenAPI()
                .info(new Info()
                        .title("API " + appName)
                        .version("v1")
                        .description("API REST do SaaS odontológico. Erros seguem RFC 9457 (Problem Details)."));
    }
}
