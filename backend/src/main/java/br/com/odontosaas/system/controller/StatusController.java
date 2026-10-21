package br.com.odontosaas.system.controller;

import br.com.odontosaas.system.dto.response.StatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/status")
@Tag(name = "Sistema", description = "Informações públicas de funcionamento")
public class StatusController {

    private final String version;
    private final Clock clock;

    public StatusController(ObjectProvider<BuildProperties> buildProperties, ObjectProvider<Clock> clock) {
        BuildProperties build = buildProperties.getIfAvailable();
        this.version = build != null ? build.getVersion() : "dev";
        this.clock = clock.getIfAvailable(Clock::systemUTC);
    }

    @GetMapping
    @Operation(summary = "Status da API", description = "Público. Não expõe dados de clínicas.")
    public StatusResponse status() {
        return new StatusResponse("UP", version, Instant.now(clock));
    }
}
