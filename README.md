# FX Service

Small Spring Boot backend service for foreign-exchange rates, client balance-based conversions, and conversion history lookup.

The service exposes REST endpoints to:

* fetch current exchange rates between two currencies
* convert money for a specific client using stored account balances
* persist conversion history
* return client balances
* search conversion history with pagination

## Technology stack

* Java 23
* Spring Boot 3.5.15
* Maven
* Spring Web
* Spring Data JPA
* H2 in-memory database
* Flyway database migrations
* Bean Validation
* SpringDoc OpenAPI / Swagger UI
* JUnit 5, Mockito, MockMvc

## How to run

### Prerequisites

* Java 23
* Maven wrapper included in the project
* Docker, if you prefer running the service in a container

### Start the application

From the project root:

```bash
./mvnw spring-boot:run
```

On Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

The application starts on:

```text
http://localhost:8080
```

### Start with Docker Compose

From the project root:

```bash
docker compose up --build
```

This builds the Java 23 Docker image and starts the service on:

```text
http://localhost:8080
```

If port `8080` is already busy, choose another host port:

```bash
HOST_PORT=18080 docker compose up --build
```

Swagger UI is available at:

```text
http://localhost:8080/swagger-ui.html
```

H2 console is available at:

```text
http://localhost:8080/h2-console
```

H2 connection details:

```text
JDBC URL: jdbc:h2:mem:fxdb
Username: sa
Password: <empty>
```

## Demo clients

Initial demo data is inserted through Flyway migration.

| Client ID  | Currency | Initial balance |
| ---------- | -------: | --------------: |
| CLIENT-001 |      USD |      10000.0000 |
| CLIENT-001 |      EUR |       8000.0000 |
| CLIENT-002 |      GBP |       5000.0000 |

No authentication is implemented. The caller identifies the client using the `X-Client-Id` header for conversion requests.

## API endpoints

### Get exchange rate

```http
GET /rates?from=USD&to=EUR
```

Example:

```bash
curl "http://localhost:8080/rates?from=USD&to=EUR"
```

Example response:

```json
{
  "from": "USD",
  "to": "EUR",
  "rate": 0.87198000,
  "timestamp": "2026-06-21T15:20:00Z"
}
```

If `from` and `to` are the same currency, the service returns rate `1.00000000` without calling the external provider.

### Convert amount

```http
POST /conversions
```

Required headers:

```text
X-Client-Id: CLIENT-001
Content-Type: application/json
```

Optional header:

```text
Idempotency-Key: any-client-generated-key
```

Example:

```bash
curl -X POST "http://localhost:8080/conversions" \
  -H "Content-Type: application/json" \
  -H "X-Client-Id: CLIENT-001" \
  -H "Idempotency-Key: demo-key-001" \
  -d '{
    "sourceAmount": 100.00,
    "sourceCurrency": "USD",
    "targetCurrency": "EUR"
  }'
```

Example response:

```json
{
  "transactionId": "e6f272df-22c0-4b87-924f-d00c1cf7e6a5",
  "sourceAmount": 100.0000,
  "sourceCurrency": "USD",
  "targetAmount": 87.1980,
  "targetCurrency": "EUR",
  "rate": 0.87198000,
  "timestamp": "2026-06-21T15:30:00Z",
  "balances": [
    {
      "currency": "EUR",
      "amount": 8087.1980
    },
    {
      "currency": "USD",
      "amount": 9900.0000
    }
  ]
}
```

### Get conversion history

```http
GET /conversions?transactionId=...&date=YYYY-MM-DD&clientId=...&page=0&size=20
```

At least one of the following filters must be provided:

* `transactionId`
* `date`
* `clientId`

Examples:

```bash
curl "http://localhost:8080/conversions?clientId=CLIENT-001&page=0&size=10"
```

```bash
curl "http://localhost:8080/conversions?date=2026-06-21&page=0&size=10"
```

```bash
curl "http://localhost:8080/conversions?transactionId=e6f272df-22c0-4b87-924f-d00c1cf7e6a5"
```

Example response:

```json
{
  "content": [
    {
      "transactionId": "e6f272df-22c0-4b87-924f-d00c1cf7e6a5",
      "clientId": "CLIENT-001",
      "sourceAmount": 100.0000,
      "sourceCurrency": "USD",
      "targetAmount": 87.1980,
      "targetCurrency": "EUR",
      "rate": 0.87198000,
      "timestamp": "2026-06-21T15:30:00Z"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1
}
```

### Get client balances

```http
GET /clients/{clientId}/balances
```

Example:

```bash
curl "http://localhost:8080/clients/CLIENT-001/balances"
```

Example response:

```json
[
  {
    "currency": "EUR",
    "amount": 8000.0000
  },
  {
    "currency": "USD",
    "amount": 10000.0000
  }
]
```

## Error response format

All API errors use a consistent JSON response body:

```json
{
  "code": "CLIENT_NOT_FOUND",
  "message": "Client not found: UNKNOWN",
  "timestamp": "2026-06-21T15:30:00Z",
  "path": "/conversions"
}
```

Main error codes:

| HTTP status | Code                   | Scenario                                                                          |
| ----------: | ---------------------- | --------------------------------------------------------------------------------- |
|         400 | VALIDATION_ERROR       | Invalid currency, missing client id, invalid amount, invalid date, missing filter |
|         404 | CLIENT_NOT_FOUND       | Client does not exist                                                             |
|         404 | BALANCE_NOT_FOUND      | Client does not have the requested currency balance                               |
|         415 | UNSUPPORTED_MEDIA_TYPE | Request body is not sent as `application/json`                                    |
|         422 | INSUFFICIENT_FUNDS     | Source balance is lower than requested source amount                              |
|         502 | PROVIDER_FAILURE       | External FX provider is unavailable or returns invalid data                       |

## Money handling

All monetary values are represented as `BigDecimal`.

The service does not use `double` or `float` for money calculations.

Current scales:

* money amounts: scale `4`
* FX rates: scale `8`

Rounding mode:

```text
RoundingMode.HALF_UP
```

On conversion:

```text
targetAmount = sourceAmount * rate
```

The calculated target amount is rounded to 4 decimal places.

## Database and migrations

The service uses H2 in-memory database by default.

Schema and demo data are created through Flyway migration:

```text
src/main/resources/db/migration/V1__init_schema.sql
```

Hibernate is configured with:

```text
ddl-auto=validate
```

This means Hibernate validates the schema but does not create or update it automatically. The schema is owned by Flyway migrations.

## Idempotency approach

`POST /conversions` supports an optional `Idempotency-Key` header.

The idempotency key is stored together with the client id in the `conversions` table.

A unique constraint exists on:

```text
client_id + idempotency_key
```

When a request is received with an existing idempotency key for the same client, the service returns the original conversion result instead of creating a new conversion.

This prevents duplicate conversion records and prevents double-debiting the source balance when the same request is retried.

Example:

```text
X-Client-Id: CLIENT-001
Idempotency-Key: demo-key-001
```

Sending the same request twice with the same key returns the same `transactionId` and does not debit the balance twice.

## Concurrency strategy

The service uses pessimistic locking for balances involved in a conversion.

For `POST /conversions`, the source and target balance rows are loaded using a JPA query with:

```text
LockModeType.PESSIMISTIC_WRITE
```

This prevents two simultaneous conversion requests for the same client and currency from spending the same balance at the same time.

The debit, credit, and conversion record insert are executed inside one database transaction using `@Transactional`.

Chosen trade-off:

* Pessimistic locking is simple and explicit for this small service.
* It is a good fit because the critical section is short.
* It avoids double-spend without introducing a more complex single-writer queue.
* For a high-throughput production system, I would revisit this depending on database load and expected contention.

The `Balance` entity also has a `@Version` field. The main concurrency protection for conversions is the pessimistic row lock.

## FX provider integration

The service currently uses:

```text
https://open.er-api.com/v6/latest/{baseCurrency}
```

No API key is required for this provider.

Provider configuration is stored in `application.yml`:

```yaml
fx:
  provider:
    base-url: https://open.er-api.com/v6/latest
    connect-timeout-ms: 3000
    read-timeout-ms: 5000
  rates:
    cache-ttl-seconds: 300
```

The HTTP client is configured with connection and read timeouts.

If the provider is unavailable or returns an invalid response, the service returns a meaningful API error with code:

```text
PROVIDER_FAILURE
```

The raw stack trace is not returned to the caller.

## Rate caching

Rates are cached in memory using a simple TTL cache.

Default TTL:

```text
300 seconds
```

Cache key:

```text
FROM_TO
```

Example:

```text
USD_EUR
```

Invalidation strategy:

* The cache entry expires automatically after the configured TTL.
* No manual invalidation endpoint is implemented.
* This is intentionally simple for the assignment scope.
* A short TTL limits stale rates while avoiding an external provider call for every request.

With more time, I would consider Caffeine or Redis depending on whether the service runs as one instance or multiple instances.

## Validation

The service validates:

* ISO-4217 currency code format
* known Java `Currency` codes
* positive conversion amounts
* sane maximum amount
* non-blank client id
* supported content type
* valid history date format
* at least one filter for conversion history search

Invalid requests return a structured `VALIDATION_ERROR`.

## Tests

The project contains:

### Unit tests

```text
ConversionServiceTest
```

Covered scenarios:

* happy-path debit and credit
* idempotency replay
* insufficient funds without saving conversion

### Integration tests

```text
ConversionControllerIntegrationTest
```

The integration tests load the Spring context and exercise the controller layer with `MockMvc`.

Covered scenarios:

* successful conversion through the REST controller
* idempotency replay through the REST controller
* concurrent idempotency replay through the REST controller
* insufficient funds through the REST controller
* unknown client and missing balance errors
* invalid request validation
* provider failure handling
* conversion history filtering by client and date

The FX provider is mocked in integration tests so the tests do not depend on network availability or live exchange rates.

Run tests:

```bash
./mvnw test
```

On Windows PowerShell:

```powershell
.\mvnw.cmd test
```

## OpenAPI / Swagger

Swagger UI is generated automatically by SpringDoc.

Open:

```text
http://localhost:8080/swagger-ui.html
```

OpenAPI JSON:

```text
http://localhost:8080/v3/api-docs
```

## Docker

A Dockerfile and Docker Compose file are included for containerized execution.

Start with Docker Compose:

```bash
docker compose up --build
```

If port `8080` is already busy:

```bash
HOST_PORT=18080 docker compose up --build
```

Stop with:

```bash
docker compose down
```

Build image:

```bash
docker build -t fx-service .
```

Run container:

```bash
docker run --rm -p 8080:8080 fx-service
```

Then open:

```text
http://localhost:8080/swagger-ui.html
```

## Configuration

Default configuration is in:

```text
src/main/resources/application.yml
```

Important properties:

```yaml
server:
  port: 8080

fx:
  provider:
    base-url: https://open.er-api.com/v6/latest
    connect-timeout-ms: 3000
    read-timeout-ms: 5000
  rates:
    cache-ttl-seconds: 300
```

## Design trade-offs

### H2 instead of PostgreSQL

I used H2 in-memory database to keep the service self-contained and easy to run without manual setup.

The persistence layer uses JPA and Flyway, so moving to PostgreSQL would mainly require adding the PostgreSQL driver, changing datasource configuration, and testing migrations against PostgreSQL.

With more time, I would add a PostgreSQL profile and Testcontainers-based integration tests.

### Simple balance model

The assignment does not require a full ledger.

The service keeps one balance row per client and currency.

This keeps the model simple and focused on the required behavior: debit, credit, and conversion history.

With more time, I would consider adding a ledger table for auditability.

### In-memory rate cache

The rate cache is a simple in-memory TTL cache.

This is enough for a single-instance assignment service.

With more time or multi-instance deployment, I would use a shared cache such as Redis or a production-grade local cache such as Caffeine.

### Pessimistic locking

I chose pessimistic row locking because it is straightforward and makes the no-double-spend guarantee explicit.

For this use case, the locked rows are few and the transaction is short.

With more time and higher expected concurrency, I would benchmark pessimistic locking against optimistic locking and retry behavior.

### No authentication

There is no authentication by design.

The client id is passed by the caller using:

```text
X-Client-Id
```

This follows the assignment scope.

## What I would do with more time

* Add PostgreSQL profile and Testcontainers-based integration tests.
* Add payload conflict detection for reused idempotency keys with different request bodies.
* Add a provider fallback strategy if the selected FX provider is unavailable.
* Add a production-grade cache implementation such as Caffeine.
* Add a ledger/audit table for balance movements.
* Add request/response examples to the OpenAPI documentation.
* Add structured logging with correlation ids.
* Add metrics for provider latency, cache hit ratio, conversion count, and failed conversions.
