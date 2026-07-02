.PHONY: up down smoke logs ps

up:
	docker compose up -d --wait --wait-timeout 90

down:
	docker compose down -v

smoke:
	bash scripts/smoke.sh

logs:
	docker compose logs -f

ps:
	docker compose ps
