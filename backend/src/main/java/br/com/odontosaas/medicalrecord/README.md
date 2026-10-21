# Módulo `medicalrecord`: Prontuário

Prontuário: anamnese, odontograma, evoluções, procedimentos realizados, documentos, anexos e orientações.

## Estrutura

- `controller/`
- `dto/request/`
- `dto/response/`
- `event/`
- `service/`
- `support/`

## Controllers

- `AttachmentController`
- `CareController`
- `ClinicalController`

## API para outros módulos

- Classes do pacote raiz (`medicalrecord`)
- `medicalrecord.event` (interface `event`)

## Eventos publicados

- `CareInstructionsSent`
- `ProcedurePerformed`

## Dependências permitidas

- `audit`
- `clinic`
- `shared`
- `tenancy`

As regras são verificadas pelo `ModularityTest` (Spring Modulith). Padrão de pastas: `controller`, `service`, `dto/request`, `dto/response`, `entity`, `repository`, `event`, `listener`, `job`, `config`, `security`, `support`.
