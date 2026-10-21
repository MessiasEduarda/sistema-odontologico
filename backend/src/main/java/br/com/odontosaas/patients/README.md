# Módulo `patients`: Pacientes

Cadastro, importação em massa e exportação LGPD de pacientes.

## Estrutura

- `controller/`
- `dto/request/`
- `dto/response/`
- `service/`
- `support/`

## Controllers

- `PatientController`

## API para outros módulos

- Classes do pacote raiz (`patients`)

## Dependências permitidas

- `audit`
- `shared`
- `tenancy`

As regras são verificadas pelo `ModularityTest` (Spring Modulith). Padrão de pastas: `controller`, `service`, `dto/request`, `dto/response`, `entity`, `repository`, `event`, `listener`, `job`, `config`, `security`, `support`.
