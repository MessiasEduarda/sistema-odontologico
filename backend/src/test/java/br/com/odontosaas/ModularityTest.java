package br.com.odontosaas;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** Falha se um módulo acessar classes internas de outro ou se houver dependência circular entre módulos. */
class ModularityTest {

    @Test
    void moduleBoundariesAreRespected() {
        ApplicationModules.of(OdontoApiApplication.class).verify();
    }
}
