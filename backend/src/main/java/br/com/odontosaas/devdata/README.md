# Módulo `devdata`: Dados de desenvolvimento

Massa de dados fictícia de desenvolvimento. Existe somente no perfil dev.

## Estrutura

- `service/`

## API para outros módulos

- Classes do pacote raiz (`devdata`)

## Dependências permitidas

- `identity`
- `identity :: config`
- `shared`
- `subscription :: service`
- `tenancy`
- `tenancy :: entity`
- `tenancy :: repository`

As regras são verificadas pelo `ModularityTest` (Spring Modulith). Padrão de pastas: `controller`, `service`, `dto/request`, `dto/response`, `entity`, `repository`, `event`, `listener`, `job`, `config`, `security`, `support`.
