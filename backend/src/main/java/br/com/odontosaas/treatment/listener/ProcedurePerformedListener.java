package br.com.odontosaas.treatment.listener;

import br.com.odontosaas.medicalrecord.event.ProcedurePerformed;
import br.com.odontosaas.treatment.service.TreatmentService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Reage ao procedimento registrado (ou anulado) no prontuário baixando o item do plano (síncrono, na mesma transação). */
@Component
class ProcedurePerformedListener {

    private final TreatmentService treatments;

    ProcedurePerformedListener(TreatmentService treatments) {
        this.treatments = treatments;
    }

    @EventListener
    void onProcedurePerformed(ProcedurePerformed event) {
        treatments.applyProcedurePerformed(event);
    }
}
