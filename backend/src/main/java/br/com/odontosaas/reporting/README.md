# Módulo `reporting`: Relatórios e painel

Painel inicial e relatórios (agenda, produção, financeiro, pacientes) com exportação CSV.

## Estrutura

- `controller/`
- `dto/response/`
- `service/`

## Controllers

- `ReportController`

## API para outros módulos

- Classes do pacote raiz (`reporting`)

## Dependências permitidas

- `audit`
- `identity :: security`
- `shared`
- `tenancy`

As regras são verificadas pelo `ModularityTest` (Spring Modulith). Padrão de pastas: `controller`, `service`, `dto/request`, `dto/response`, `entity`, `repository`, `event`, `listener`, `job`, `config`, `security`, `support`.
