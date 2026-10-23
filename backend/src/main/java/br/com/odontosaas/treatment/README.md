# Módulo `treatment`: Orçamentos

Orçamentos e planos de tratamento (rascunho, apresentado, aprovado).

## Estrutura

- `controller/`
- `dto/request/`
- `dto/response/`
- `event/`
- `listener/`
- `repository/`
- `service/`

## Controllers

- `TreatmentController`

## API para outros módulos

- Classes do pacote raiz (`treatment`)
- `treatment.dto.response` (interface `dto`)
- `treatment.event` (interface `event`)
- `treatment.service` (interface `service`)

## Eventos publicados

- `TreatmentPlanApproved`

## Eventos consumidos

- `ProcedurePerformed`

## Dependências permitidas

- `audit`
- `medicalrecord :: event`
- `shared`
- `tenancy`

As regras são verificadas pelo `ModularityTest` (Spring Modulith). Padrão de pastas: `controller`, `service`, `dto/request`, `dto/response`, `entity`, `repository`, `event`, `listener`, `job`, `config`, `security`, `support`.
