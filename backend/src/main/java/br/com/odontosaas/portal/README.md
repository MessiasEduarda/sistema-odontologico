# Módulo `portal`: Portal do paciente

Acesso do paciente por link: consultas, termos e contato com a clínica.

## Estrutura

- `controller/`
- `dto/request/`
- `dto/response/`
- `repository/`
- `service/`

## Controllers

- `PortalController`

## API para outros módulos

- Classes do pacote raiz (`portal`)

## Dependências permitidas

- `audit`
- `communication :: service`
- `consent :: dto`
- `consent :: service`
- `scheduling :: dto`
- `scheduling :: service`
- `shared`
- `tenancy`

As regras são verificadas pelo `ModularityTest` (Spring Modulith). Padrão de pastas: `controller`, `service`, `dto/request`, `dto/response`, `entity`, `repository`, `event`, `listener`, `job`, `config`, `security`, `support`.
