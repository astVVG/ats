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

## Documentación

- [Guía de contribución](CONTRIBUTING.md)
- [Etapas del proyecto](docs/stages.md)
