package br.com.odontosaas.support;

import br.com.odontosaas.identity.ClinicProvisioning;
import br.com.odontosaas.tenancy.entity.Tenant;
import br.com.odontosaas.tenancy.repository.TenantRepository;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cria dados FICTÍCIOS e únicos por teste (o banco é compartilhado entre os testes da execução, então nomes, e-mails e
 * slugs nunca se repetem).
 */
@Component
public class TestData {

    public static final String PASSWORD = "Senha-de-Teste-123";
    private static final AtomicInteger SEQ = new AtomicInteger();

    public record Person(UUID id, String email) {}

    private final ClinicProvisioning provisioning;
    private final TenantRepository tenants;
    private final TransactionTemplate tx;
    private final br.com.odontosaas.platform.SubscriptionService subscriptions;

    public TestData(
            ClinicProvisioning provisioning,
            TenantRepository tenants,
            TransactionTemplate tx,
            br.com.odontosaas.platform.SubscriptionService subscriptions) {
        this.provisioning = provisioning;
        this.tenants = tenants;
        this.tx = tx;
        this.subscriptions = subscriptions;
    }

    public UUID clinic() {
        String slug = "clinica-teste-" + unique();
        return tx.execute(status -> {
            Tenant tenant = tenants.saveAndFlush(new Tenant("Clínica " + slug, slug));
            provisioning.createDefaultRoles(tenant.getId(), null);
            subscriptions.startTrial(tenant.getId(), "CLINICA_PLUS");
            return tenant.getId();
        });
    }

    public Person user() {
        return user(false);
    }

    public Person platformAdmin() {
        return user(true);
    }

    private Person user(boolean platformAdmin) {
        String email = "pessoa" + unique() + "@teste.example";
        UUID id = tx.execute(s -> provisioning.createUser(email, "Pessoa Teste", PASSWORD, platformAdmin));
        return new Person(id, email);
    }

    public Person member(UUID clinicId, String roleCode) {
        Person person = user();
        addMember(clinicId, person, roleCode);
        return person;
    }

    public void addMember(UUID clinicId, Person person, String roleCode) {
        tx.executeWithoutResult(s -> provisioning.addMember(clinicId, person.id(), roleCode));
    }

    public static String unique() {
        return System.nanoTime() % 1_000_000_000 + "-" + SEQ.incrementAndGet();
    }
}
