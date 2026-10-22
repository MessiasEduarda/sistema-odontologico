# Módulo `platformadmin`: Administração da plataforma

Indicadores, clínicas (criação e situação), auditoria da plataforma e primeiro administrador.

## Estrutura

- `controller/`
- `dto/request/`
- `dto/response/`
- `repository/`
- `service/`

## Controllers

- `PlatformAdminController`
- `PlatformTenantController`

## API para outros módulos

- Classes do pacote raiz (`platformadmin`)

## Dependências permitidas

- `audit`
- `identity`
- `identity :: config`
- `identity :: security`
- `shared`
- `subscription :: service`
- `tenancy`
- `tenancy :: entity`
- `tenancy :: repository`

As regras são verificadas pelo `ModularityTest` (Spring Modulith). Padrão de pastas: `controller`, `service`, `dto/request`, `dto/response`, `entity`, `repository`, `event`, `listener`, `job`, `config`, `security`, `support`.
