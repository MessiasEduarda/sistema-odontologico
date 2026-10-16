package br.com.odontosaas.identity.access;

import static br.com.odontosaas.identity.Permission.*;

import br.com.odontosaas.identity.Permission;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Papéis criados automaticamente em toda clínica nova. Fonte da matriz documentada em
 * docs/seguranca/matriz-permissoes.md — alterações aqui exigem atualizar a documentação e os testes.
 */
public final class RoleTemplates {

    public static final String OWNER = "OWNER";
    public static final String DENTIST = "DENTIST";
    public static final String ASSISTANT = "ASSISTANT";
    public static final String RECEPTION = "RECEPTION";
    public static final String FINANCE = "FINANCE";

    public record Template(String name, Set<Permission> permissions) {}

    public static final Map<String, Template> ALL = build();

    private RoleTemplates() {}

    private static Map<String, Template> build() {
        Map<String, Template> map = new LinkedHashMap<>();
        map.put(OWNER, new Template("Administrador da clínica", EnumSet.allOf(Permission.class)));
        map.put(
                DENTIST,
                new Template(
                        "Dentista",
                        EnumSet.of(
                                PATIENTS_READ,
                                PATIENTS_WRITE,
                                SCHEDULE_READ,
                                SCHEDULE_WRITE,
                                CLINICAL_RECORDS_READ,
                                CLINICAL_RECORDS_WRITE,
                                TREATMENTS_READ,
                                TREATMENTS_WRITE,
                                INVENTORY_READ,
                                USERS_READ)));
        map.put(
                ASSISTANT,
                new Template(
                        "Auxiliar / ASB",
                        EnumSet.of(
                                PATIENTS_READ,
                                PATIENTS_WRITE,
                                SCHEDULE_READ,
                                SCHEDULE_WRITE,
                                CLINICAL_RECORDS_READ,
                                TREATMENTS_READ,
                                INVENTORY_READ,
                                INVENTORY_WRITE)));
        map.put(
                RECEPTION,
                new Template(
                        "Recepção",
                        EnumSet.of(
                                PATIENTS_READ,
                                PATIENTS_WRITE,
                                SCHEDULE_READ,
                                SCHEDULE_WRITE,
                                TREATMENTS_READ,
                                TREATMENTS_WRITE,
                                FINANCE_READ)));
        map.put(
                FINANCE,
                new Template(
                        "Financeiro",
                        EnumSet.of(PATIENTS_READ, FINANCE_READ, FINANCE_WRITE, REPORTS_READ, TREATMENTS_READ)));
        return Map.copyOf(map);
    }
}
