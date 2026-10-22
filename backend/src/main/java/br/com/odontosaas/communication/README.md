# Módulo `communication`: Comunicação

Mensagens por e-mail (confirmação, lembrete, orientações, links), modelos e rotinas de envio.

## Estrutura

- `config/`
- `controller/`
- `dto/request/`
- `dto/response/`
- `job/`
- `listener/`
- `repository/`
- `service/`

## Controllers

- `CommunicationController`

## API para outros módulos

- Classes do pacote raiz (`communication`)
- `communication.service` (interface `service`)

## Eventos consumidos

- `AppointmentChanged`
- `CareInstructionsSent`
- `ConsentLinkRequested`

## Dependências permitidas

- `audit`
- `consent :: event`
- `medicalrecord :: event`
- `scheduling :: event`
- `scheduling :: service`
- `shared`
- `tenancy`

As regras são verificadas pelo `ModularityTest` (Spring Modulith). Padrão de pastas: `controller`, `service`, `dto/request`, `dto/response`, `entity`, `repository`, `event`, `listener`, `job`, `config`, `security`, `support`.
