# Módulo `inventory`: Estoque

Produtos, movimentações e alertas de estoque e validade.

## Estrutura

- `controller/`
- `dto/request/`
- `dto/response/`
- `repository/`
- `service/`

## Controllers

- `InventoryController`

## API para outros módulos

- Classes do pacote raiz (`inventory`)

## Dependências permitidas

- `audit`
- `shared`
- `tenancy`

As regras são verificadas pelo `ModularityTest` (Spring Modulith). Padrão de pastas: `controller`, `service`, `dto/request`, `dto/response`, `entity`, `repository`, `event`, `listener`, `job`, `config`, `security`, `support`.
