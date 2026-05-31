# Banking System - Run Notes

## Build

```bash
mvn -Dmaven.test.skip=true package
docker compose build
```

## Start

```bash
docker compose up -d
docker compose ps
```

## Health URLs

- API Gateway: http://localhost:8080/actuator/health
- Auth Service: http://localhost:8081/actuator/health
- Profile Service: http://localhost:8082/actuator/health
- Account Service: http://localhost:8083/actuator/health
- Transaction Service: http://localhost:8084/actuator/health
- Notification Service: http://localhost:8085/actuator/health
- Eureka: http://localhost:8761
- Config Server: http://localhost:8888/actuator/health

## Stop

```bash
docker compose down
```

Default dev secrets are included in `.env` and `secrets/` so the stack can start immediately. Change them before using this outside local development.
