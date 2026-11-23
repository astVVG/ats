# Mini ATS

Mini ATS es un sistema de seguimiento de candidatos (Applicant Tracking System) para gestionar vacantes y candidatos, y mover cada postulación en un tablero Kanban por las etapas `APPLIED`, `INTERVIEW`, `OFFER`, `HIRED` y `REJECTED`. Incluye historial de cambios, notas y métricas.

## Stack

| Capa          | Tecnología                          |
| ------------- | ----------------------------------- |
| Backend       | Spring Boot 3, Java 21, Maven       |
| Frontend      | React, TypeScript                   |
| Base de datos | PostgreSQL 16 (esquema con Flyway)  |

## Estructura

```text
ats/
  backend/    API REST (Spring Boot)
  frontend/   Aplicación web (React + TypeScript)
  docs/       Documentación del proyecto
    adr/      Registros de decisiones de arquitectura
  infra/      Infraestructura y despliegue (Etapa 6)
  .github/    Plantillas de GitHub
```

## Primeros pasos

Esta sección se completará conforme avancen las etapas del proyecto.

Para preparar el entorno local (hooks de git y atajos de `make`), consulta [Herramientas locales](CONTRIBUTING.md#12-herramientas-locales).

## Base de datos de desarrollo

1. Copia `.env.example` a `.env` y asigna una contraseña en `DB_PASSWORD`. El `.env` también define `JWT_SECRET` (valor aleatorio de al menos 32 caracteres) y las variables `ADMIN_EMAIL`, `ADMIN_PASSWORD` y `ADMIN_NAME` del administrador inicial, que se crea al arrancar si no existe.
2. Levanta PostgreSQL 16:

   ```bash
   docker compose -f docker-compose.dev.yml up -d
   ```

3. Desde `backend/`, ejecuta el backend:

   ```bash
   ./mvnw spring-boot:run
   ```

Las pruebas del backend usan Testcontainers, por lo que requieren Docker en ejecución.

## Frontend

```bash
cd frontend
npm install
npm run dev
```

La aplicación queda disponible en http://localhost:5173. Comandos de verificación:

```bash
npm run lint
npm run test
npm run build
```

Los tipos de la API se generan desde el OpenAPI del backend:

```bash
npm run api:types
```

Requiere el backend en ejecución en http://localhost:8080. El archivo generado (`src/shared/api/schema.d.ts`) se versiona: vuelve a ejecutar el comando y versiona el resultado cada vez que cambie la API.

## Documentación

- [Guía de contribución](CONTRIBUTING.md)
- [Etapas del proyecto](docs/stages.md)
