package br.com.odontosaas.identity.service;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.identity.repository.TermsRepository;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.shared.RequestInfo;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TermsService {

    public static final String CURRENT_VERSION = "2026-10-15";

    private final TermsRepository terms;
    private final AuditService audit;

    public TermsService(TermsRepository terms, AuditService audit) {
        this.terms = terms;
        this.audit = audit;
    }

    public boolean hasAcceptedCurrent(UUID userId) {
        return terms.hasAccepted(userId, CURRENT_VERSION);
    }

    @Transactional
    public void accept(UUID userId, String version) {
        if (!CURRENT_VERSION.equals(version)) {
            throw ApiException.conflict(
                    "TERMS_OUTDATED", "Os termos foram atualizados. Recarregue a página para ler a versão vigente.");
        }
        int inserted = terms.insertIfAbsent(userId, version, RequestInfo.clientIp(), RequestInfo.userAgent());
        if (inserted > 0) {
            audit.recordGlobal(AuditAction.TERMS_ACCEPTED, userId, "terms", null, Map.of("version", version));
        }
    }
}
