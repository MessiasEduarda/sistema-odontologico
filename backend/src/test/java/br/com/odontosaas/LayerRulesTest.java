package br.com.odontosaas;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Regras de camada do backend, verificadas no bytecode: o acesso ao banco mora só nos repositories (e em poucas peças
 * de infraestrutura), controllers não falam com repositories e DTOs não conhecem as camadas acima deles.
 */
class LayerRulesTest {

    private static final com.tngtech.archunit.core.domain.JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("br.com.odontosaas");

    @Test
    void servicesControllersListenersAndJobsDoNotTouchTheDatabaseDirectly() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage("..controller..", "..service..", "..listener..", "..job..")
                // Massa de dados de desenvolvimento: SQL de seed, existe só no perfil dev.
                .and()
                .resideOutsideOfPackage("br.com.odontosaas.devdata..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework.jdbc..", "javax.sql..")
                .because("o SQL fica nas classes de repository do módulo");
        rule.check(CLASSES);
    }

    @Test
    void controllersDoNotUseRepositoriesDirectly() {
        noClasses()
                .that()
                .resideInAPackage("..controller..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..repository..")
                .because("o controller só fala com o service; o service decide como persistir")
                .check(CLASSES);
    }

    @Test
    void dtosDoNotKnowTheLayersAboveThem() {
        noClasses()
                .that()
                .resideInAPackage("..dto..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..controller..", "..service..", "..repository..", "..entity..")
                .because("DTO é só contrato de entrada e saída")
                .check(CLASSES);
    }
}
