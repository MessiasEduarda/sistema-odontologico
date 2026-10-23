# Módulo `portal`: Portal do paciente

Acesso do paciente por link: consultas, termos, documentos, orçamentos, recibos, anexos liberados e contato com a clínica.

## Estrutura

- `controller/`
- `dto/request/`
- `dto/response/`
- `repository/`
- `service/`

## Controllers

- `PortalAccountController`
- `PortalAuthController`
- `PortalController`

## API para outros módulos

- Classes do pacote raiz (`portal`)

## Dependências permitidas

- `audit`
- `communication :: service`
- `consent :: dto`
- `consent :: service`
- `finance :: dto`
- `finance :: service`
- `medicalrecord :: dto`
- `medicalrecord :: service`
- `patients :: event`
- `scheduling :: dto`
- `scheduling :: service`
- `shared`
- `tenancy`
- `treatment :: dto`
- `treatment :: service`

As regras são verificadas pelo `ModularityTest` (Spring Modulith). Padrão de pastas: `controller`, `service`, `dto/request`, `dto/response`, `entity`, `repository`, `event`, `listener`, `job`, `config`, `security`, `support`.
