package br.com.odontosaas.helpdesk;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.odontosaas.helpdesk.service.SupportHours;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class SupportHoursTest {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    @Test
    void countsOnlyWeekdaysFromEightToSix() {
        assertThat(add(LocalDateTime.of(2026, 10, 21, 9, 0), 120)).isEqualTo(LocalDateTime.of(2026, 10, 21, 11, 0));
        assertThat(add(LocalDateTime.of(2026, 10, 23, 17, 0), 120)).isEqualTo(LocalDateTime.of(2026, 10, 26, 9, 0));
        assertThat(add(LocalDateTime.of(2026, 10, 24, 15, 0), 120)).isEqualTo(LocalDateTime.of(2026, 10, 26, 10, 0));
        assertThat(add(LocalDateTime.of(2026, 10, 21, 6, 30), 600)).isEqualTo(LocalDateTime.of(2026, 10, 21, 18, 0));
        assertThat(add(LocalDateTime.of(2026, 10, 21, 19, 0), 1800)).isEqualTo(LocalDateTime.of(2026, 10, 26, 18, 0));
    }

    @Test
    void impactBecomesPriorityWithItsDeadline() {
        assertThat(SupportHours.priorityFromImpact("BLOCKING")).isEqualTo("URGENT");
        assertThat(SupportHours.priorityFromImpact("DEGRADED")).isEqualTo("HIGH");
        assertThat(SupportHours.priorityFromImpact("QUESTION")).isEqualTo("NORMAL");
        assertThat(SupportHours.priorityFromImpact("SUGGESTION")).isEqualTo("LOW");
        assertThat(SupportHours.slaMinutes("URGENT")).isEqualTo(120);
        assertThat(SupportHours.slaMinutes("LOW")).isEqualTo(1800);
    }

    private static LocalDateTime add(LocalDateTime start, long minutes) {
        Instant due = SupportHours.addBusinessMinutes(start.atZone(SP).toInstant(), minutes, SP);
        return LocalDateTime.ofInstant(due, SP);
    }
}
