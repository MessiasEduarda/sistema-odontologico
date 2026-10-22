# Módulo `identity`: Identidade e acesso

Login, sessão, senha, convites, membros e papéis, termos de uso e e-mails de identidade.

## Estrutura

- `config/`
- `controller/`
- `dto/request/`
- `dto/response/`
- `entity/`
- `job/`
- `repository/`
- `security/`
- `service/`
- `support/`

## Controllers

- `AuthController`
- `ClinicAccessController`
- `PublicInvitationController`

## API para outros módulos

- Classes do pacote raiz (`identity`)
- `identity.config` (interface `config`)
- `identity.security` (interface `security`)

## Dependências permitidas

- `audit`
- `shared`
- `tenancy`
- `tenancy :: entity`
- `tenancy :: repository`

As regras são verificadas pelo `ModularityTest` (Spring Modulith). Padrão de pastas: `controller`, `service`, `dto/request`, `dto/response`, `entity`, `repository`, `event`, `listener`, `job`, `config`, `security`, `support`.
