package br.com.odontosaas.inventory.controller;

import br.com.odontosaas.inventory.dto.request.MovementRequest;
import br.com.odontosaas.inventory.dto.request.ProductRequest;
import br.com.odontosaas.inventory.dto.response.Alerts;
import br.com.odontosaas.inventory.dto.response.MovementView;
import br.com.odontosaas.inventory.dto.response.ProductView;
import br.com.odontosaas.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
@Tag(name = "Estoque")
public class InventoryController {

    private static final String READ = "hasAuthority('inventory:read')";
    private static final String WRITE = "hasAuthority('inventory:write')";

    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    @GetMapping("/products")
    @PreAuthorize(READ)
    public List<ProductView> products(
            @RequestParam(required = false) String q, @RequestParam(defaultValue = "false") boolean lowStock) {
        return service.products(q, lowStock);
    }

    @GetMapping("/products/{id}")
    @PreAuthorize(READ)
    public ProductView product(@PathVariable UUID id) {
        return service.product(id);
    }

    @PostMapping("/products")
    @PreAuthorize(WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public ProductView create(@Valid @RequestBody ProductRequest request) {
        return service.create(request);
    }

    @PutMapping("/products/{id}")
    @PreAuthorize(WRITE)
    public ProductView update(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return service.update(id, request);
    }

    @GetMapping("/products/{id}/movements")
    @PreAuthorize(READ)
    public List<MovementView> movements(@PathVariable UUID id) {
        return service.movements(id);
    }

    @PostMapping("/products/{id}/movements")
    @PreAuthorize(WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Entrada, saída ou ajuste",
            description = "ADJUST: quantity = saldo contado. 422 INSUFFICIENT_STOCK se a saída deixaria negativo.")
    public ProductView move(@PathVariable UUID id, @Valid @RequestBody MovementRequest request) {
        return service.move(id, request);
    }

    @GetMapping("/alerts")
    @PreAuthorize(READ)
    @Operation(summary = "Estoque baixo e lotes vencendo em até 60 dias")
    public Alerts alerts() {
        return service.alerts();
    }
}
