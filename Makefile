COMPOSE := docker compose -f docker-compose.dev.yml

.DEFAULT_GOAL := help

.PHONY: help setup db-up db-down run-back run-front test test-back test-front lint format api-types

help: ## List available targets
	@grep -E '^[a-zA-Z_-]+:.*## ' Makefile | awk 'BEGIN {FS = ":.*## "} {printf "  %-12s %s\n", $$1, $$2}'

setup: ## Install root tooling (git hooks) and frontend dependencies
	npm install
	cd frontend && npm install

db-up: ## Start the development MySQL
	$(COMPOSE) up -d

db-down: ## Stop the development MySQL (keeps data)
	$(COMPOSE) down

run-back: ## Run the backend (dev profile)
	cd backend && ./mvnw spring-boot:run

run-front: ## Run the frontend dev server
	cd frontend && npm run dev

test: test-back test-front ## Run backend and frontend checks

test-back: ## Backend build, tests and format check (requires Docker)
	cd backend && ./mvnw -B verify

test-front: ## Frontend lint, tests and build
	cd frontend && npm run lint && npm run test && npm run build

lint: ## Lint and format check for frontend and backend
	cd frontend && npm run lint
	cd backend && ./mvnw spotless:check

format: ## Format frontend and backend code
	cd frontend && npm run format
	cd backend && ./mvnw spotless:apply

api-types: ## Generate API types (requires the backend running)
	cd frontend && npm run api:types
