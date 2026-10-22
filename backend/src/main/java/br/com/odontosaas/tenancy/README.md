# Módulo `tenancy`: Clínicas (multi-tenant)

Clínica (tenant), contexto da clínica na requisição e isolamento por RLS no banco.

## Estrutura

- `config/`
- `entity/`
- `repository/`
- `support/`

## API para outros módulos

- Classes do pacote raiz (`tenancy`)
- `tenancy.entity` (interface `entity`)
- `tenancy.repository` (interface `repository`)

## Dependências permitidas

- `shared`

As regras são verificadas pelo `ModularityTest` (Spring Modulith). Padrão de pastas: `controller`, `service`, `dto/request`, `dto/response`, `entity`, `repository`, `event`, `listener`, `job`, `config`, `security`, `support`.
