package br.com.odontosaas.reporting.service;

import br.com.odontosaas.identity.security.AuthenticatedUser;
import br.com.odontosaas.identity.security.Permission;
import br.com.odontosaas.reporting.dto.response.Dashboard;
import br.com.odontosaas.reporting.dto.response.DashboardAlert;
import br.com.odontosaas.reporting.dto.response.TodayAppointment;
import br.com.odontosaas.reporting.repository.DashboardRepository;
import br.com.odontosaas.reporting.repository.DashboardRepository.OverdueRow;
import br.com.odontosaas.tenancy.TenantContext;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Painel inicial: só inclui indicadores que a pessoa tem permissão de ver. */
@Service
public class DashboardService {

    private final DashboardRepository repository;

    public DashboardService(DashboardRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard(AuthenticatedUser user) {
        UUID tenantId = TenantContext.requireTenantId();
        String zone = repository.timeZone(tenantId);
        boolean schedule = user.has(Permission.SCHEDULE_READ);
        boolean patients = user.has(Permission.PATIENTS_READ);
        boolean finance = user.has(Permission.FINANCE_READ);
        boolean inventory = user.has(Permission.INVENTORY_READ);
        boolean clinical = user.has(Permission.CLINICAL_RECORDS_READ) || user.has(Permission.PATIENTS_WRITE);

        Long appointmentsToday = null, confirmedToday = null, completedToday = null;
        List<TodayAppointment> today = List.of();
        List<DashboardAlert> alerts = new ArrayList<>();
        if (schedule) {
            today = repository.todayAppointments(tenantId, zone);
            appointmentsToday = (long) today.size();
            confirmedToday = today.stream()
                    .filter(a -> List.of("CONFIRMED", "ARRIVED", "IN_PROGRESS", "COMPLETED")
                            .contains(a.status()))
                    .count();
            completedToday =
                    today.stream().filter(a -> "COMPLETED".equals(a.status())).count();
            long unconfirmed = repository.countUnconfirmedTomorrow(tenantId, zone);
            if (unconfirmed > 0) {
                alerts.add(new DashboardAlert(
                        "UNCONFIRMED_TOMORROW",
                        "warning",
                        unconfirmed + " consulta(s) de amanhã ainda sem confirmação.",
                        unconfirmed));
            }
        }
        Long activePatients = null, newPatients = null;
        if (patients) {
            activePatients = repository.countActivePatients(tenantId);
            newPatients = repository.countNewPatientsThisMonth(tenantId, zone);
        }
        if (clinical) {
            long pending = repository.countPendingConsents(tenantId);
            if (pending > 0) {
                alerts.add(new DashboardAlert(
                        "PENDING_CONSENTS", "info", pending + " termo(s) aguardando assinatura.", pending));
            }
        }
        BigDecimal received = null, overdue = null;
        if (finance) {
            received = repository.receivedThisMonth(tenantId, zone);
            OverdueRow o = repository.overdueInstallments(tenantId);
            overdue = o.amount();
            if (o.count() > 0) {
                alerts.add(new DashboardAlert(
                        "OVERDUE_INSTALLMENTS", "danger", o.count() + " parcela(s) em atraso.", o.count()));
            }
            long payablesSoon = repository.countPayablesDueSoon(tenantId);
            if (payablesSoon > 0) {
                alerts.add(new DashboardAlert(
                        "PAYABLES_DUE",
                        "warning",
                        payablesSoon + " conta(s) a pagar vencendo em até 3 dias.",
                        payablesSoon));
            }
        }
        Long lowStock = null;
        if (inventory) {
            lowStock = repository.countLowStock(tenantId);
            if (lowStock > 0) {
                alerts.add(new DashboardAlert(
                        "LOW_STOCK", "warning", lowStock + " produto(s) com estoque baixo.", lowStock));
            }
        }
        return new Dashboard(
                appointmentsToday,
                confirmedToday,
                completedToday,
                activePatients,
                newPatients,
                received,
                overdue,
                lowStock,
                today,
                alerts);
    }
}
