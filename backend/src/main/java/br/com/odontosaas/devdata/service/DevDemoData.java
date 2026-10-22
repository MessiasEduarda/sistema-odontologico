package br.com.odontosaas.devdata.service;

import br.com.odontosaas.shared.Ids;
import br.com.odontosaas.tenancy.TenantContext;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class DevDemoData {

    private final JdbcClient jdbc;

    DevDemoData(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void fill(UUID tenantId, UUID dentistUserId, UUID ownerUserId) {
        TenantContext.runAs(tenantId, ownerUserId, () -> {
            Long existing = jdbc.sql("select count(*) from professionals where tenant_id = ?")
                    .param(tenantId)
                    .query(Long.class)
                    .single();
            if (existing > 0) return null;

            jdbc.sql("""
                            insert into clinic_settings (tenant_id, legal_name, phone, email, city, state)
                            values (?, ?, '(11) 3333-0000', 'contato@clinica.example', 'São Paulo', 'SP')
                            on conflict (tenant_id) do nothing
                            """)
                    .params(tenantId, "Clínica Fictícia de Demonstração Ltda.")
                    .update();

            UUID ana = professional(tenantId, ownerUserId, "Dra. Ana Exemplo", "Ortodontia", "#9C7A5B");
            UUID bruno = professional(tenantId, dentistUserId, "Dr. Bruno Teste", "Clínica geral", "#4F7A6A");
            for (UUID p : List.of(ana, bruno)) {
                for (int day = 1; day <= 5; day++) {
                    schedule(tenantId, p, day, LocalTime.of(8, 0), LocalTime.of(12, 0));
                    schedule(tenantId, p, day, LocalTime.of(13, 0), LocalTime.of(18, 0));
                }
            }
            UUID room1 = room(tenantId, "Consultório 1");
            UUID room2 = room(tenantId, "Consultório 2");

            UUID avaliacao = procedure(tenantId, "Consulta / avaliação", "Clínico", "120.00", 30);
            UUID limpeza = procedure(tenantId, "Limpeza (profilaxia)", "Prevenção", "180.00", 40);
            UUID resina = procedure(tenantId, "Restauração em resina", "Dentística", "250.00", 50);
            procedure(tenantId, "Tratamento de canal (unirradicular)", "Endodontia", "750.00", 90);
            UUID extracao = procedure(tenantId, "Extração simples", "Cirurgia", "300.00", 45);
            procedure(tenantId, "Clareamento em consultório", "Estética", "900.00", 60);
            procedure(tenantId, "Aplicação de flúor", "Prevenção", "80.00", 20);
            procedure(tenantId, "Raspagem periodontal (por sextante)", "Periodontia", "200.00", 40);
            procedure(tenantId, "Coroa em porcelana", "Prótese", "1800.00", 60);
            procedure(tenantId, "Radiografia periapical", "Radiologia", "40.00", 10);
            procedure(tenantId, "Selante (por dente)", "Prevenção", "70.00", 15);

            String[][] people = {
                {"Carlos Fictício Souza", "1985-02-14", "11900000001"},
                {"Mariana Exemplo Lima", "1992-07-30", "11900000002"},
                {"João Demonstração Alves", "1978-11-05", "11900000003"},
                {"Beatriz Teste Rocha", "2001-04-22", "11900000004"},
                {"Pedro Modelo Costa", "1969-09-12", "11900000005"},
                {"Luana Amostra Dias", "1995-12-01", "11900000006"},
                {"Rafael Fictício Nunes", "1988-06-18", "11900000007"},
                {"Sofia Exemplo Martins", "2015-03-09", "11900000008"},
            };
            List<UUID> patients = new ArrayList<>();
            for (String[] p : people) {
                UUID id = Ids.newId();
                boolean minor = LocalDate.parse(p[1]).isAfter(LocalDate.now().minusYears(18));
                jdbc.sql("""
                                insert into patients (id, tenant_id, full_name, birth_date, mobile, email, city, state,
                                    allow_email, guardian_name, guardian_phone)
                                values (?, ?, ?, ?, ?, ?, 'São Paulo', 'SP', true, ?, ?)
                                """)
                        .params(
                                id,
                                tenantId,
                                p[0],
                                LocalDate.parse(p[1]),
                                p[2],
                                p[0].split(" ")[0].toLowerCase() + "@paciente.example",
                                minor ? "Responsável Fictício" : null,
                                minor ? "11900000099" : null)
                        .update();
                patients.add(id);
            }

            ZoneId zone = ZoneId.of("America/Sao_Paulo");
            LocalDate today = LocalDate.now(zone);
            LocalDate next = today.getDayOfWeek().getValue() >= 5
                    ? today.plusDays(8 - today.getDayOfWeek().getValue())
                    : today.plusDays(1);
            appointment(
                    tenantId,
                    patients.get(0),
                    bruno,
                    room1,
                    limpeza,
                    today.atTime(9, 0).atZone(zone),
                    40,
                    "CONFIRMED");
            appointment(
                    tenantId,
                    patients.get(1),
                    ana,
                    room2,
                    avaliacao,
                    today.atTime(10, 0).atZone(zone),
                    30,
                    "SCHEDULED");
            appointment(
                    tenantId,
                    patients.get(2),
                    bruno,
                    room1,
                    resina,
                    today.atTime(14, 0).atZone(zone),
                    50,
                    "SCHEDULED");
            appointment(
                    tenantId,
                    patients.get(3),
                    ana,
                    room2,
                    avaliacao,
                    today.atTime(15, 30).atZone(zone),
                    30,
                    "SCHEDULED");
            appointment(
                    tenantId,
                    patients.get(4),
                    bruno,
                    room1,
                    extracao,
                    next.atTime(8, 30).atZone(zone),
                    45,
                    "SCHEDULED");
            appointment(
                    tenantId,
                    patients.get(5),
                    ana,
                    room2,
                    limpeza,
                    next.atTime(11, 0).atZone(zone),
                    40,
                    "SCHEDULED");

            UUID template = Ids.newId();
            jdbc.sql("""
                            insert into consent_templates (id, tenant_id, family_id, title, body, version, created_by)
                            values (?, ?, ?, ?, ?, 1, ?)
                            """)
                    .params(
                            template,
                            tenantId,
                            template,
                            "Consentimento para tratamento odontológico (modelo de exemplo)",
                            """
                            MODELO DE EXEMPLO — revise com assessoria jurídica antes de usar.

                            Eu, {{paciente}}, CPF {{cpf}}, declaro que fui informado(a) pelo(a) profissional \
                            {{profissional}} ({{cro}}) sobre o procedimento: {{procedimento}}, seus objetivos, \
                            alternativas, riscos e cuidados necessários, e que tive a oportunidade de esclarecer \
                            minhas dúvidas.

                            Declaro que as informações de saúde que prestei são verdadeiras e autorizo a realização \
                            do procedimento na {{clinica}}.

                            Data: {{data}}""",
                            ownerUserId)
                    .update();

            UUID plan = Ids.newId();
            jdbc.sql("""
                            insert into treatment_plans (id, tenant_id, patient_id, professional_id, title, status,
                                payment_conditions, created_by)
                            values (?, ?, ?, ?, 'Tratamento restaurador', 'PROPOSED', 'Até 3x sem juros', ?)
                            """)
                    .params(plan, tenantId, patients.get(2), bruno, ownerUserId)
                    .update();
            planItem(tenantId, plan, resina, "Restauração em resina — dente 36", 36, "250.00", 0);
            planItem(tenantId, plan, resina, "Restauração em resina — dente 46", 46, "250.00", 1);
            planItem(tenantId, plan, limpeza, "Limpeza (profilaxia)", null, "180.00", 2);

            product(tenantId, "Luvas de procedimento (M)", "Descartáveis", "cx", "5", "12");
            product(tenantId, "Resina composta A2", "Materiais", "un", "3", "2");
            product(tenantId, "Anestésico (tubete)", "Medicamentos", "cx", "4", "10");
            product(tenantId, "Sugador descartável", "Descartáveis", "pct", "5", "20");
            return null;
        });
    }

    private UUID professional(UUID tenantId, UUID userId, String name, String specialty, String color) {
        UUID id = Ids.newId();
        jdbc.sql("""
                        insert into professionals (id, tenant_id, user_id, name, cro, cro_state, specialty, color)
                        values (?, ?, ?, ?, ?, 'SP', ?, ?)
                        """)
                .params(
                        id,
                        tenantId,
                        userId,
                        name,
                        String.valueOf(10000 + (int) (Math.random() * 89999)),
                        specialty,
                        color)
                .update();
        return id;
    }

    private void schedule(UUID tenantId, UUID professionalId, int weekday, LocalTime start, LocalTime end) {
        jdbc.sql("""
                        insert into professional_schedules (id, tenant_id, professional_id, weekday, start_time, end_time)
                        values (?, ?, ?, ?, ?, ?)
                        """)
                .params(Ids.newId(), tenantId, professionalId, weekday, start, end)
                .update();
    }

    private UUID room(UUID tenantId, String name) {
        UUID id = Ids.newId();
        jdbc.sql("insert into rooms (id, tenant_id, name) values (?, ?, ?)")
                .params(id, tenantId, name)
                .update();
        return id;
    }

    private UUID procedure(UUID tenantId, String name, String category, String price, int minutes) {
        UUID id = Ids.newId();
        jdbc.sql("""
                        insert into procedures (id, tenant_id, name, category, default_price, duration_minutes)
                        values (?, ?, ?, ?, ?, ?)
                        """)
                .params(id, tenantId, name, category, new BigDecimal(price), minutes)
                .update();
        return id;
    }

    private void appointment(
            UUID tenantId,
            UUID patient,
            UUID professional,
            UUID room,
            UUID procedure,
            ZonedDateTime start,
            int minutes,
            String status) {
        jdbc.sql("""
                        insert into appointments (id, tenant_id, patient_id, professional_id, room_id, procedure_id,
                            starts_at, ends_at, status) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        Ids.newId(),
                        tenantId,
                        patient,
                        professional,
                        room,
                        procedure,
                        Timestamp.from(start.toInstant()),
                        Timestamp.from(start.plusMinutes(minutes).toInstant()),
                        status)
                .update();
    }

    private void planItem(
            UUID tenantId, UUID plan, UUID procedure, String description, Integer tooth, String price, int order) {
        jdbc.sql("""
                        insert into treatment_plan_items (id, tenant_id, plan_id, procedure_id, description, tooth,
                            quantity, unit_price, sort_order) values (?, ?, ?, ?, ?, ?, 1, ?, ?)
                        """)
                .params(Ids.newId(), tenantId, plan, procedure, description, tooth, new BigDecimal(price), order)
                .update();
    }

    private void product(UUID tenantId, String name, String category, String unit, String min, String stock) {
        jdbc.sql("""
                        insert into products (id, tenant_id, name, category, unit, min_stock, current_stock)
                        values (?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(Ids.newId(), tenantId, name, category, unit, new BigDecimal(min), new BigDecimal(stock))
                .update();
    }
}
