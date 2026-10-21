package br.com.odontosaas;

import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.core.env.StandardEnvironment;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OdontoApiApplication {

    public static void main(String[] args) {
        requireExplicitProfile(args);
        SpringApplication.run(OdontoApiApplication.class, args);
    }

    /**
     * Falha fechada: sem perfil explícito (dev ou prod) a aplicação não sobe. Antes o perfil padrão era "dev", e um JAR
     * iniciado sem a variável ficava com seed, senha padrão do admin e cookie sem Secure.
     */
    private static void requireExplicitProfile(String[] args) {
        boolean viaArgs = Arrays.stream(args).anyMatch(a -> a.startsWith("--spring.profiles.active="));
        // StandardEnvironment lê spring.profiles.active de variável de ambiente e de propriedade de sistema (-D).
        if (!viaArgs && new StandardEnvironment().getActiveProfiles().length == 0) {
            throw new IllegalStateException(
                    "Nenhum perfil Spring ativo. Defina SPRING_PROFILES_ACTIVE=dev (local) ou prod."
                            + " Local: ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev.");
        }
    }
}
