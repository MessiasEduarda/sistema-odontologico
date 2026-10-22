package br.com.odontosaas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OdontoApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(OdontoApiApplication.class, args);
    }
}
