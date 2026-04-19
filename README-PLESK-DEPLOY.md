# Deploy su Plesk con Docker (DB + Java)

Questo progetto e` una Spring Boot app che usa PostgreSQL + Flyway (migrations automatiche al boot).

## Prerequisiti

1. Plesk installato su un VPS/dedicato con accesso admin.
2. Estensione Plesk **Docker** installata (Extensions -> Docker).
3. Un dominio o sottodominio puntato al server.

## File inclusi

- `Dockerfile` (build multi-stage: gradle -> JRE)
- `docker-compose.yml` (2 container: `db` + `app`)
- `.env.example` (variabili da copiare in `.env`)

## Passi (alta affidabilita`)

1. Copia `.env.example` in `.env` e imposta password forti.
2. Sul server (via SSH) nella cartella del progetto:
   - `docker compose up -d --build`
3. Verifica health:
   - `curl -s http://127.0.0.1:18080/actuator/health`
4. In Plesk configura il reverse proxy del dominio verso `http://127.0.0.1:18080`.
5. Attiva Let's Encrypt dal pannello Plesk.

## Note HTTPS

Se imposti `APP_REQUIRE_HTTPS=true`, lascia anche `APP_TRUST_FORWARDED_HEADERS=true` e assicurati che il proxy
inoltri `X-Forwarded-Proto: https`, altrimenti rischi redirect loop.

