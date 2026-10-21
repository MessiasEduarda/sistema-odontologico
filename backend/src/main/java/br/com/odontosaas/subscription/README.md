# Módulo `subscription`: Planos e assinatura

Planos, assinatura de cada clínica, limites, bloqueio por plano e por pagamento vencido.

## Estrutura

- `config/`
- `controller/`
- `dto/request/`
- `dto/response/`
- `service/`

## Controllers

- `SubscriptionController`

## API para outros módulos

- Classes do pacote raiz (`subscription`)
- `subscription.service` (interface `service`)

## Dependências permitidas

- `audit`
- `shared`
- `tenancy`

As regras são verificadas pelo `ModularityTest` (Spring Modulith). Padrão de pastas: `controller`, `service`, `dto/request`, `dto/response`, `entity`, `repository`, `event`, `listener`, `job`, `config`, `security`, `support`.
