# Módulo `consent`: Termos de consentimento

Modelos e termos de consentimento, assinatura presencial e por link público.

## Estrutura

- `controller/`
- `dto/request/`
- `dto/response/`
- `event/`
- `repository/`
- `service/`
- `support/`

## Controllers

- `ConsentController`
- `PublicConsentController`

## API para outros módulos

- Classes do pacote raiz (`consent`)
- `consent.dto.response` (interface `dto`)
- `consent.event` (interface `event`)
- `consent.service` (interface `service`)

## Eventos publicados

- `ConsentLinkRequested`

## Dependências permitidas

- `audit`
- `shared`
- `tenancy`

As regras são verificadas pelo `ModularityTest` (Spring Modulith). Padrão de pastas: `controller`, `service`, `dto/request`, `dto/response`, `entity`, `repository`, `event`, `listener`, `job`, `config`, `security`, `support`.
