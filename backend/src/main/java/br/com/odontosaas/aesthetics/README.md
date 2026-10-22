# Módulo `aesthetics`: Harmonização orofacial

Aplicações de harmonização com produto, lote, validade e regiões (rastreabilidade).

## Estrutura

- `controller/`
- `dto/request/`
- `dto/response/`
- `repository/`
- `service/`

## Controllers

- `AestheticController`

## API para outros módulos

- Classes do pacote raiz (`aesthetics`)

## Dependências permitidas

- `audit`
- `clinic`
- `shared`
- `tenancy`

As regras são verificadas pelo `ModularityTest` (Spring Modulith). Padrão de pastas: `controller`, `service`, `dto/request`, `dto/response`, `entity`, `repository`, `event`, `listener`, `job`, `config`, `security`, `support`.
