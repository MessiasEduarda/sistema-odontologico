package br.com.odontosaas.portal.listener;

import br.com.odontosaas.patients.event.PatientEmailChanged;
import br.com.odontosaas.patients.event.PatientRegistered;
import br.com.odontosaas.portal.service.PortalAccountService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class PatientPortalListener {

    private final PortalAccountService accounts;

    public PatientPortalListener(PortalAccountService accounts) {
        this.accounts = accounts;
    }

    @EventListener
    public void on(PatientRegistered event) {
        accounts.registered(event.tenantId(), event.patientId());
    }

    @EventListener
    public void on(PatientEmailChanged event) {
        accounts.emailChanged(event.tenantId(), event.patientId());
    }
}
