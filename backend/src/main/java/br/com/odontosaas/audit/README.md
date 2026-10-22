# Módulo `audit`: Auditoria

Trilha de auditoria imutável das ações da clínica e da plataforma; consulta dos eventos da clínica.

## Estrutura

- `controller/`
- `dto/response/`

## Controllers

- `AuditController`

## API para outros módulos

- Classes do pacote raiz (`audit`)

## Dependências permitidas

- `shared`
- `tenancy`

As regras são verificadas pelo `ModularityTest` (Spring Modulith). Padrão de pastas: `controller`, `service`, `dto/request`, `dto/response`, `entity`, `repository`, `event`, `listener`, `job`, `config`, `security`, `support`.
