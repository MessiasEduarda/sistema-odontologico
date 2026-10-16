package br.com.odontosaas.platform;

import br.com.odontosaas.shared.Ids;
import br.com.odontosaas.tenancy.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Financeiro de DEMONSTRAÇÃO (perfil dev), 100% fictício, gravado no banco como se tivesse sido lançado pela tela:
 * contas a receber quitadas, parceladas, vencidas, com pagamento parcial e cancelada; fornecedores e contas a pagar
 * pagas, em aberto e vencidas. Serve para testar o financeiro de ponta a ponta (receber, estornar, emitir recibo,
 * pagar conta, relatórios). Só roda se a clínica ainda não tiver nenhum lançamento financeiro.
 */
@Component
class DevFinanceDemo {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private static final String[][] EXTRA_PATIENTS = {
        {"Helena Fictícia Prado", "1990-05-11", "11900000021"},
        {"Otávio Exemplo Ramos", "1983-08-23", "11900000022"},
        {"Clara Demonstração Faria", "1999-01-17", "11900000023"},
        {"Igor Amostra Teles", "1975-10-04", "11900000024"},
    };

    private final JdbcClient jdbc;

    DevFinanceDemo(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void fill(UUID tenantId, UUID ownerUserId) {
        if (ownerUserId == null) return;
        TenantContext.runAs(tenantId, ownerUserId, () -> {
            long existing =
                    jdbc.sql("""
                            select (select count(*) from receivables where tenant_id = ?)
                                 + (select count(*) from payables where tenant_id = ?)
                            """).params(tenantId, tenantId).query(Long.class).single();
            if (existing > 0) return null;

            List<UUID> patients = patients(tenantId);
            LocalDate today = LocalDate.now(ZONE);

            // Quitada em 3x (uma por mês).
            UUID r1 = receivable(tenantId, ownerUserId, patients.get(0), "Clareamento em consultório", "900.00");
            List<UUID> i1 = installments(tenantId, r1, "900.00", 3, today.minusMonths(2));
            pay(tenantId, ownerUserId, i1.get(0), "300.00", "PIX", today.minusMonths(2));
            pay(tenantId, ownerUserId, i1.get(1), "300.00", "PIX", today.minusMonths(1));
            pay(tenantId, ownerUserId, i1.get(2), "300.00", "CREDIT_CARD", today.minusDays(2));

            // Parcelada em 4x: 2 pagas, 1 vencida, 1 a vencer.
            UUID r2 =
                    receivable(tenantId, ownerUserId, patients.get(1), "Tratamento restaurador (4 dentes)", "1000.00");
            List<UUID> i2 = installments(
                    tenantId, r2, "1000.00", 4, today.minusMonths(2).minusDays(10));
            pay(
                    tenantId,
                    ownerUserId,
                    i2.get(0),
                    "250.00",
                    "DEBIT_CARD",
                    today.minusMonths(2).minusDays(10));
            pay(
                    tenantId,
                    ownerUserId,
                    i2.get(1),
                    "250.00",
                    "CASH",
                    today.minusMonths(1).minusDays(9));

            // À vista, vencida há 20 dias (inadimplência).
            UUID r3 = receivable(tenantId, ownerUserId, patients.get(2), "Extração simples", "300.00");
            installments(tenantId, r3, "300.00", 1, today.minusDays(20));

            // Pagamento parcial: 100 de 250, saldo vence em 5 dias.
            UUID r4 = receivable(tenantId, ownerUserId, patients.get(3), "Restauração em resina", "250.00");
            List<UUID> i4 = installments(tenantId, r4, "250.00", 1, today.plusDays(5));
            pay(tenantId, ownerUserId, i4.get(0), "100.00", "PIX", today.minusDays(1));

            // Quitada hoje, à vista.
            UUID r5 = receivable(tenantId, ownerUserId, patients.get(0), "Consulta / avaliação", "120.00");
            List<UUID> i5 = installments(tenantId, r5, "120.00", 1, today);
            pay(tenantId, ownerUserId, i5.get(0), "120.00", "CASH", today);

            // Em aberto, 2x a vencer.
            UUID r6 = receivable(tenantId, ownerUserId, patients.get(1), "Limpeza (profilaxia) e flúor", "260.00");
            installments(tenantId, r6, "260.00", 2, today.plusDays(10));

            // Cancelada (lançada por engano).
            UUID r7 = receivable(tenantId, ownerUserId, patients.get(2), "Radiografia periapical", "40.00");
            installments(tenantId, r7, "40.00", 1, today.plusDays(3));
            jdbc.sql("update installments set status = 'CANCELLED' where tenant_id = ? and receivable_id = ?")
                    .params(tenantId, r7)
                    .update();
            jdbc.sql("""
                            update receivables set status = 'CANCELLED', cancel_reason = 'Lançada em duplicidade'
                            where tenant_id = ? and id = ?
                            """).params(tenantId, r7).update();

            UUID dental = supplier(tenantId, "Dental Fictícia Distribuidora Ltda.", "(11) 4000-0001");
            UUID lab = supplier(tenantId, "Laboratório de Prótese Exemplo", "(11) 4000-0002");
            UUID imob = supplier(tenantId, "Imobiliária Modelo (fictícia)", "(11) 4000-0003");
            payable(
                    tenantId,
                    ownerUserId,
                    imob,
                    "Aluguel do consultório",
                    "ALUGUEL",
                    "3500.00",
                    today.withDayOfMonth(1).minusMonths(1).plusDays(4),
                    today.withDayOfMonth(1).minusMonths(1).plusDays(4),
                    "BANK_TRANSFER");
            payable(
                    tenantId,
                    ownerUserId,
                    imob,
                    "Aluguel do consultório",
                    "ALUGUEL",
                    "3500.00",
                    today.plusDays(7),
                    null,
                    null);
            payable(
                    tenantId,
                    ownerUserId,
                    dental,
                    "Resinas e anestésicos",
                    "MATERIAIS",
                    "842.90",
                    today.minusDays(3),
                    null,
                    null);
            payable(
                    tenantId,
                    ownerUserId,
                    lab,
                    "Coroa em porcelana — paciente fictício",
                    "LABORATORIO",
                    "650.00",
                    today.minusDays(12),
                    today.minusDays(12),
                    "PIX");
            payable(
                    tenantId,
                    ownerUserId,
                    null,
                    "Simples Nacional",
                    "IMPOSTOS",
                    "1180.35",
                    today.plusDays(15),
                    null,
                    null);
            payable(
                    tenantId,
                    ownerUserId,
                    dental,
                    "Luvas e sugadores",
                    "MATERIAIS",
                    "312.00",
                    today.minusDays(25),
                    today.minusDays(24),
                    "BOLETO");
            return null;
        });
    }

    /** Pacientes ativos da clínica; cria alguns fictícios se houver menos de 4. */
    private List<UUID> patients(UUID tenantId) {
        List<UUID> ids = new ArrayList<>(
                jdbc.sql("select id from patients where tenant_id = ? and active order by created_at limit 4")
                        .param(tenantId)
                        .query(UUID.class)
                        .list());
        for (int i = 0; ids.size() < 4; i++) {
            String[] p = EXTRA_PATIENTS[i];
            UUID id = Ids.newId();
            jdbc.sql("""
                            insert into patients (id, tenant_id, full_name, birth_date, mobile, email, city, state)
                            values (?, ?, ?, ?, ?, ?, 'São Paulo', 'SP')
                            """)
                    .params(
                            id,
                            tenantId,
                            p[0],
                            LocalDate.parse(p[1]),
                            p[2],
                            p[0].split(" ")[0].toLowerCase() + "@paciente.example")
                    .update();
            ids.add(id);
        }
        return ids;
    }

    private UUID receivable(UUID tenantId, UUID userId, UUID patientId, String description, String total) {
        UUID id = Ids.newId();
        jdbc.sql("""
                        insert into receivables (id, tenant_id, patient_id, description, total, created_by, created_at)
                        values (?, ?, ?, ?, ?, ?, now() - interval '70 days')
                        """)
                .params(id, tenantId, patientId, description, new BigDecimal(total), userId)
                .update();
        return id;
    }

    /** Parcelas mensais iguais (a última absorve o arredondamento). */
    private List<UUID> installments(UUID tenantId, UUID receivableId, String total, int count, LocalDate firstDue) {
        BigDecimal sum = new BigDecimal(total);
        BigDecimal each = sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);
        List<UUID> ids = new ArrayList<>();
        for (int n = 1; n <= count; n++) {
            BigDecimal amount = n == count ? sum.subtract(each.multiply(BigDecimal.valueOf(count - 1))) : each;
            UUID id = Ids.newId();
            jdbc.sql("""
                            insert into installments (id, tenant_id, receivable_id, number, due_date, amount)
                            values (?, ?, ?, ?, ?, ?)
                            """)
                    .params(id, tenantId, receivableId, n, firstDue.plusMonths(n - 1), amount)
                    .update();
            ids.add(id);
        }
        return ids;
    }

    /** Recebimento com os mesmos efeitos da tela: atualiza a parcela e quita a conta quando tudo foi pago. */
    private void pay(UUID tenantId, UUID userId, UUID installmentId, String amount, String method, LocalDate day) {
        BigDecimal value = new BigDecimal(amount);
        jdbc.sql("""
                        insert into payments (id, tenant_id, installment_id, amount, method, paid_at, created_by)
                        values (?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        Ids.newId(),
                        tenantId,
                        installmentId,
                        value,
                        method,
                        Timestamp.from(
                                day.isBefore(LocalDate.now(ZONE))
                                        ? day.atTime(10, 30).atZone(ZONE).toInstant()
                                        : Instant.now().minusSeconds(60)),
                        userId)
                .update();
        jdbc.sql("""
                        update installments set paid_amount = paid_amount + ?,
                            status = case when paid_amount + ? >= amount then 'PAID' else status end
                        where tenant_id = ? and id = ?
                        """).params(value, value, tenantId, installmentId).update();
        jdbc.sql("""
                        update receivables r set status = 'PAID'
                        where r.tenant_id = ? and r.id = (select receivable_id from installments where id = ?)
                          and not exists (select 1 from installments i where i.tenant_id = r.tenant_id
                                          and i.receivable_id = r.id and i.status = 'OPEN')
                        """).params(tenantId, installmentId).update();
    }

    private UUID supplier(UUID tenantId, String name, String phone) {
        UUID id = Ids.newId();
        jdbc.sql("insert into suppliers (id, tenant_id, name, phone) values (?, ?, ?, ?)")
                .params(id, tenantId, name, phone)
                .update();
        return id;
    }

    private void payable(
            UUID tenantId,
            UUID userId,
            UUID supplierId,
            String description,
            String category,
            String amount,
            LocalDate due,
            LocalDate paidOn,
            String method) {
        jdbc.sql("""
                        insert into payables (id, tenant_id, supplier_id, description, category, amount, due_date,
                            status, paid_at, payment_method, created_by)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        Ids.newId(),
                        tenantId,
                        supplierId,
                        description,
                        category,
                        new BigDecimal(amount),
                        due,
                        paidOn == null ? "OPEN" : "PAID",
                        paidOn == null
                                ? null
                                : Timestamp.from(
                                        paidOn.atTime(9, 0).atZone(ZONE).toInstant()),
                        method,
                        userId)
                .update();
    }
}
