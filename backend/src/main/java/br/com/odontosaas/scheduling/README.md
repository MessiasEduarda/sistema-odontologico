# Módulo `scheduling`: Agenda

Consultas, disponibilidade, bloqueios de agenda, lista de espera e links do paciente.

## Estrutura

- `controller/`
- `dto/request/`
- `dto/response/`
- `event/`
- `repository/`
- `service/`

## Controllers

- `AppointmentController`
- `ScheduleBlockController`
- `WaitlistController`

## API para outros módulos

- Classes do pacote raiz (`scheduling`)
- `scheduling.dto.response` (interface `dto`)
- `scheduling.event` (interface `event`)
- `scheduling.service` (interface `service`)

## Eventos publicados

- `AppointmentChanged`

## Dependências permitidas

- `audit`
- `shared`
- `tenancy`

As regras são verificadas pelo `ModularityTest` (Spring Modulith). Padrão de pastas: `controller`, `service`, `dto/request`, `dto/response`, `entity`, `repository`, `event`, `listener`, `job`, `config`, `security`, `support`.
