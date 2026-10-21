# Módulo `finance`: Financeiro

Recebíveis, parcelas, pagamentos, estornos, contas a pagar, fornecedores e fluxo de caixa.

## Estrutura

- `controller/`
- `dto/request/`
- `dto/response/`
- `service/`
- `support/`

## Controllers

- `FinanceController`

## API para outros módulos

- Classes do pacote raiz (`finance`)

## Eventos consumidos

- `TreatmentPlanApproved`

## Dependências permitidas

- `audit`
- `shared`
- `tenancy`
- `treatment :: event`

As regras são verificadas pelo `ModularityTest` (Spring Modulith). Padrão de pastas: `controller`, `service`, `dto/request`, `dto/response`, `entity`, `repository`, `event`, `listener`, `job`, `config`, `security`, `support`.
