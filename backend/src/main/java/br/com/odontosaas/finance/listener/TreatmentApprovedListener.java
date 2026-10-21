package br.com.odontosaas.finance.listener;

import br.com.odontosaas.finance.service.FinanceService;
import br.com.odontosaas.treatment.event.TreatmentPlanApproved;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Reage à aprovação de um orçamento gerando a conta a receber (síncrono, na transação de quem publica o evento). */
@Component
class TreatmentApprovedListener {

    private final FinanceService finance;

    TreatmentApprovedListener(FinanceService finance) {
        this.finance = finance;
    }

    @EventListener
    void onTreatmentApproved(TreatmentPlanApproved event) {
        finance.createReceivableForApprovedPlan(event);
    }
}
