package br.com.odontosaas.portal.dto.response;

import br.com.odontosaas.consent.dto.response.ConsentView;
import br.com.odontosaas.finance.dto.response.ReceivableView;
import br.com.odontosaas.medicalrecord.dto.response.AttachmentView;
import br.com.odontosaas.medicalrecord.dto.response.DocumentView;
import br.com.odontosaas.treatment.dto.response.PlanView;
import java.util.List;

public record PortalRecords(
        PortalClinic clinic,
        PortalPatient patient,
        List<DocumentView> documents,
        List<ConsentView> consents,
        List<PlanView> plans,
        List<ReceivableView> receivables,
        List<AttachmentView> attachments) {}
