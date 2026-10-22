package br.com.odontosaas.finance.controller;

import br.com.odontosaas.finance.dto.request.PayRequest;
import br.com.odontosaas.finance.dto.request.PayableRequest;
import br.com.odontosaas.finance.dto.request.PaymentRequest;
import br.com.odontosaas.finance.dto.request.ReasonRequest;
import br.com.odontosaas.finance.dto.request.ReceivableRequest;
import br.com.odontosaas.finance.dto.request.RefundRequest;
import br.com.odontosaas.finance.dto.request.SupplierRequest;
import br.com.odontosaas.finance.dto.response.CashFlowView;
import br.com.odontosaas.finance.dto.response.FinanceSummary;
import br.com.odontosaas.finance.dto.response.InstallmentRow;
import br.com.odontosaas.finance.dto.response.PayableView;
import br.com.odontosaas.finance.dto.response.ReceivableSummary;
import br.com.odontosaas.finance.dto.response.ReceivableView;
import br.com.odontosaas.finance.dto.response.SupplierView;
import br.com.odontosaas.finance.service.FinanceService;
import br.com.odontosaas.finance.service.PayableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Financeiro")
public class FinanceController {

    private static final String READ = "hasAuthority('finance:read')";
    private static final String WRITE = "hasAuthority('finance:write')";
    private static final String SUPPLIERS_READ = "hasAnyAuthority('finance:read', 'inventory:read')";
    private static final String SUPPLIERS_WRITE = "hasAnyAuthority('finance:write', 'inventory:write')";

    private final FinanceService finance;
    private final PayableService payables;

    public FinanceController(FinanceService finance, PayableService payables) {
        this.finance = finance;
        this.payables = payables;
    }

    @GetMapping("/api/v1/finance/summary")
    @PreAuthorize(READ)
    @Operation(summary = "Resumo do mês: recebido, a receber, inadimplência, contas a pagar")
    public FinanceSummary summary() {
        return finance.summary();
    }

    @GetMapping("/api/v1/finance/cash-flow")
    @PreAuthorize(READ)
    @Operation(summary = "Fluxo de caixa diário (recebimentos − estornos − pagamentos)")
    public CashFlowView cashFlow(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return finance.cashFlow(from, to);
    }

    @GetMapping("/api/v1/finance/receivables")
    @PreAuthorize(READ)
    public List<ReceivableSummary> receivables(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) String q) {
        return finance.receivables(status, patientId, q);
    }

    @GetMapping("/api/v1/finance/receivables/{id}")
    @PreAuthorize(READ)
    public ReceivableView receivable(@PathVariable UUID id) {
        return finance.receivable(id);
    }

    @PostMapping("/api/v1/finance/receivables")
    @PreAuthorize(WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Conta a receber avulsa, parcelada mensalmente")
    public ReceivableView createReceivable(@Valid @RequestBody ReceivableRequest request) {
        return finance.createReceivable(request);
    }

    @PostMapping("/api/v1/finance/receivables/{id}/cancel")
    @PreAuthorize(WRITE)
    @Operation(summary = "Cancela conta sem recebimentos", description = "422 RECEIVABLE_HAS_PAYMENTS")
    public ReceivableView cancelReceivable(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        return finance.cancelReceivable(id, request.reason());
    }

    @GetMapping("/api/v1/finance/installments")
    @PreAuthorize(READ)
    @Operation(summary = "Parcelas por vencimento", description = "status: OPEN (padrão), OVERDUE, PAID, ALL")
    public List<InstallmentRow> installments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String status) {
        return finance.installments(from, to, status);
    }

    @PostMapping("/api/v1/finance/installments/{id}/payments")
    @PreAuthorize(WRITE)
    @Operation(summary = "Registra recebimento (total ou parcial)")
    public ReceivableView pay(@PathVariable UUID id, @Valid @RequestBody PaymentRequest request) {
        return finance.pay(id, request);
    }

    @PostMapping("/api/v1/finance/payments/{id}/refunds")
    @PreAuthorize(WRITE)
    @Operation(summary = "Estorna recebimento (total ou parcial)")
    public ReceivableView refund(@PathVariable UUID id, @Valid @RequestBody RefundRequest request) {
        return finance.refund(id, request.amount(), request.reason());
    }

    @GetMapping("/api/v1/finance/payables")
    @PreAuthorize(READ)
    public List<PayableView> payables(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return payables.list(status, from, to);
    }

    @PostMapping("/api/v1/finance/payables")
    @PreAuthorize(WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public PayableView createPayable(@Valid @RequestBody PayableRequest request) {
        return payables.create(request);
    }

    @PutMapping("/api/v1/finance/payables/{id}")
    @PreAuthorize(WRITE)
    public PayableView updatePayable(@PathVariable UUID id, @Valid @RequestBody PayableRequest request) {
        return payables.update(id, request);
    }

    @PostMapping("/api/v1/finance/payables/{id}/pay")
    @PreAuthorize(WRITE)
    public PayableView payPayable(@PathVariable UUID id, @Valid @RequestBody PayRequest request) {
        return payables.pay(id, request.method(), request.paidAt());
    }

    @PostMapping("/api/v1/finance/payables/{id}/cancel")
    @PreAuthorize(WRITE)
    public PayableView cancelPayable(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        return payables.cancel(id, request.reason());
    }

    @GetMapping("/api/v1/suppliers")
    @PreAuthorize(SUPPLIERS_READ)
    public List<SupplierView> suppliers() {
        return payables.suppliers();
    }

    @PostMapping("/api/v1/suppliers")
    @PreAuthorize(SUPPLIERS_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierView createSupplier(@Valid @RequestBody SupplierRequest request) {
        return payables.createSupplier(request);
    }

    @PutMapping("/api/v1/suppliers/{id}")
    @PreAuthorize(SUPPLIERS_WRITE)
    public SupplierView updateSupplier(@PathVariable UUID id, @Valid @RequestBody SupplierRequest request) {
        return payables.updateSupplier(id, request);
    }
}
