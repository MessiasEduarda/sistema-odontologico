# Módulo `clinic`: Clínica

Dados da clínica, profissionais (e agenda de atendimento), salas, procedimentos e logo; verificação de autoria clínica.

## Estrutura

- `controller/`
- `dto/request/`
- `dto/response/`
- `service/`

## Controllers

- `ClinicController`

## API para outros módulos

- Classes do pacote raiz (`clinic`)

## Dependências permitidas

- `audit`
- `identity :: security`
- `shared`
- `tenancy`

As regras são verificadas pelo `ModularityTest` (Spring Modulith). Padrão de pastas: `controller`, `service`, `dto/request`, `dto/response`, `entity`, `repository`, `event`, `listener`, `job`, `config`, `security`, `support`.
