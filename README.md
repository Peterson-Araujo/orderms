# msorder

Microsserviço de pedidos construído com **Spring Boot**. Ele consome eventos de pedidos criados a partir de uma fila **RabbitMQ**, persiste os dados no **MongoDB** e expõe uma API REST para consultar os pedidos de um cliente, com paginação e total acumulado.

## Visão geral

```
┌──────────────┐   evento    ┌───────────────────────┐   grava   ┌──────────┐
│  Produtor    │ ──────────▶ │       RabbitMQ        │           │ MongoDB  │
│ (outro MS)   │             │ msorder-created-queue │           │tb_orders │
└──────────────┘             └──────────┬────────────┘           └────▲─────┘
                                        │ consome                     │
                                        ▼                             │
                              ┌───────────────────┐   save()          │
                              │      msorder      │ ──────────────────┘
                              │  (Spring Boot)    │
                              └─────────▲─────────┘
                                        │ GET /customers/{id}/orders
                                     Cliente HTTP
```

**Fluxo:**

1. Um evento `OrderCreatedEvent` chega na fila `msorder-created-queue`.
2. O `OrderCreatedListener` recebe a mensagem e chama o `OrderService`.
3. O serviço calcula o total do pedido (soma de `preço × quantidade` dos itens) e salva o documento na collection `tb_orders`.
4. A API REST permite listar os pedidos de um cliente e ver o valor total gasto por ele.

## Tecnologias

- Java 17
- Spring Boot 4.1.1
- Spring AMQP (RabbitMQ)
- Spring Data MongoDB
- Spring Web MVC
- Lombok
- Maven (com Maven Wrapper)
- Docker Compose (infraestrutura local)
- JUnit 5, Mockito e AssertJ (testes)

## Pré-requisitos

- JDK 17 ou superior
- Docker e Docker Compose

## Como executar

### 1. Subir a infraestrutura (MongoDB e RabbitMQ)

```bash
docker compose -f local/docker-compose.yml up -d
```

| Serviço  | Porta                                | Observação                       |
|----------|--------------------------------------|----------------------------------|
| MongoDB  | `27017`                              | usuário `admin` / senha `123`    |
| RabbitMQ | `5672` (AMQP) e `15672` (painel web) | painel em http://localhost:15672 |

> As credenciais acima são apenas para ambiente de desenvolvimento local.

### 2. Subir a aplicação

Linux / macOS:

```bash
./mvnw spring-boot:run
```

Windows:

```bash
mvnw.cmd spring-boot:run
```

A aplicação sobe por padrão em `http://localhost:8080`.

## Configuração

As configurações ficam em `src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: msorder
  mongodb:
    database: msorder
    host: localhost
    port: 27017
    username: admin
    password: "123"
    authentication-database: admin
    data:
      mongodb:
        auto-index-creation: true
```

## Mensageria

- **Fila:** `msorder-created-queue`
- **Formato:** JSON

Exemplo de mensagem a ser publicada na fila:

```json
{
  "codigoPedido": 1001,
  "codigoCliente": 2,
  "itens": [
    { "produto": "lápis", "quantidade": 100, "preco": 1.10 },
    { "produto": "caderno", "quantidade": 10, "preco": 1.00 }
  ]
}
```

Para testar, você pode publicar essa mensagem pelo painel do RabbitMQ (`Queues` → `msorder-created-queue` → `Publish message`), informando `content_type = application/json` nas propriedades.

## API

### Listar pedidos de um cliente

```
GET /customers/{customerId}/orders
```

**Parâmetros de query:**

| Parâmetro  | Padrão | Descrição                 |
|------------|--------|---------------------------|
| `page`     | `0`    | Número da página (base 0) |
| `pageSize` | `10`   | Itens por página          |

**Exemplo:**

```bash
curl "http://localhost:8080/customers/2/orders?page=0&pageSize=10"
```

**Resposta:**

```json
{
  "summary": {
    "totalOnOrders": 120.00
  },
  "data": [
    {
      "orderId": 1001,
      "customerId": 2,
      "total": 120.00
    }
  ],
  "pagination": {
    "page": 0,
    "pageSize": 10,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

- `summary.totalOnOrders`: soma do total de todos os pedidos do cliente.
- `data`: pedidos da página solicitada.
- `pagination`: metadados da paginação.

## Modelo de dados

Collection `tb_orders`:

```json
{
  "_id": 1001,
  "customerId": 2,
  "total": "120.00",
  "items": [
    { "product": "lápis", "quantity": 100, "price": "1.10" },
    { "product": "caderno", "quantity": 10, "price": "1.00" }
  ]
}
```

- Valores monetários são armazenados como `Decimal128` para evitar erros de arredondamento.
- Existe um índice em `customerId` (`customer_id_index`), criado automaticamente na inicialização.

## Estrutura do projeto

```
src
├── main/java/br/petersonaraujo/msorder
│   ├── config        # Configuração do RabbitMQ (fila e conversor JSON)
│   ├── controller    # Endpoints REST e DTOs de resposta
│   ├── entity        # Entidades mapeadas no MongoDB
│   ├── listener      # Consumidor da fila e DTOs do evento
│   ├── repository    # Repositório Spring Data MongoDB
│   └── service       # Regras de negócio
└── test/java/br/petersonaraujo/msorder
    ├── controller    # Testes da camada web (@WebMvcTest)
    └── service       # Testes unitários do OrderService
```

## Testes

O projeto possui testes unitários para as camadas de serviço e de controller.

| Classe                | Tipo                                   | O que cobre                                                                                  |
|-----------------------|----------------------------------------|----------------------------------------------------------------------------------------------|
| `OrderServiceTest`    | Unitário (JUnit 5 + Mockito)           | Salvamento do pedido e cálculo do total, listagem paginada e total agregado por cliente      |
| `OrderControllerTest` | Camada web (`@WebMvcTest` + `MockMvc`) | Resposta 200 com `summary`, `data` e `pagination`, paginação padrão e customizada, 404 e 400 |

Os testes usam mocks para o `OrderRepository`, o `MongoTemplate` e o `OrderService`, portanto **não precisam de MongoDB nem de RabbitMQ rodando**.

Para executar todos os testes:

```bash
./mvnw test
```

Para executar uma classe específica:

```bash
./mvnw test -Dtest=OrderServiceTest
```

> O `MsorderApplicationTests` (`contextLoads`) sobe o contexto completo da aplicação, então ele depende do MongoDB e do RabbitMQ do `docker-compose` estarem ativos.

## Autor

**Peterson Araujo**
