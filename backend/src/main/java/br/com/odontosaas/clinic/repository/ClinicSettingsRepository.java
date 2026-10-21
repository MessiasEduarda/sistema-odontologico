package br.com.odontosaas.clinic.repository;

import br.com.odontosaas.clinic.dto.request.SettingsRequest;
import br.com.odontosaas.clinic.dto.response.SettingsView;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Acesso a dados das configurações da clínica (nome em {@code tenants} e preferências em {@code clinic_settings}). */
@Repository
public class ClinicSettingsRepository {

    private final JdbcClient jdbc;

    public ClinicSettingsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public SettingsView find(UUID tenantId) {
        return jdbc.sql("""
                        select t.name, s.legal_name, s.document, s.phone, s.email, s.zip_code, s.address, s.city,
                               s.state,
                               coalesce(s.default_appointment_minutes, 30) as default_appointment_minutes,
                               coalesce(s.reminders_enabled, true) as reminders_enabled,
                               coalesce(s.reminder_hours_before, 24) as reminder_hours_before,
                               t.time_zone,
                               coalesce(s.aesthetics_enabled, false) as aesthetics_enabled,
                               l.updated_at as logo_updated_at
                        from tenants t left join clinic_settings s on s.tenant_id = t.id
                        left join clinic_logos l on l.tenant_id = t.id
                        where t.id = ?
                        """).param(tenantId).query(SettingsView.class).single();
    }

    public void updateTenantName(String name, UUID tenantId) {
        jdbc.sql("update tenants set name = ?, updated_at = now() where id = ?")
                .params(name, tenantId)
                .update();
    }

    public void upsert(UUID tenantId, SettingsRequest r) {
        jdbc.sql("""
                        insert into clinic_settings (tenant_id, legal_name, document, phone, email, zip_code, address,
                            city, state, default_appointment_minutes, reminders_enabled, reminder_hours_before,
                            aesthetics_enabled)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        on conflict (tenant_id) do update set
                            legal_name = excluded.legal_name, document = excluded.document, phone = excluded.phone,
                            email = excluded.email, zip_code = excluded.zip_code, address = excluded.address,
                            city = excluded.city, state = excluded.state,
                            default_appointment_minutes = excluded.default_appointment_minutes,
                            reminders_enabled = excluded.reminders_enabled,
                            reminder_hours_before = excluded.reminder_hours_before,
                            aesthetics_enabled = excluded.aesthetics_enabled, updated_at = now()
                        """)
                .params(
                        tenantId,
                        r.legalName(),
                        r.document(),
                        r.phone(),
                        r.email(),
                        r.zipCode(),
                        r.address(),
                        r.city(),
                        r.state(),
                        r.defaultAppointmentMinutes(),
                        r.remindersEnabled(),
                        r.reminderHoursBefore(),
                        r.aestheticsEnabled())
                .update();
    }
}
