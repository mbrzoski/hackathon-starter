.PHONY: build test run-backend run-frontend demo demo-tunnel smoke e2e e2e-demo clean-demo recordings

# Build the backend without running tests.
build:
	cd backend && ./mvnw -DskipTests package

# Run all backend tests (unit, ArchUnit) via verify.
test:
	cd backend && ./mvnw verify

# Start the backend with the dev profile.
run-backend:
	cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Start the three frontend apps: senior :4201, listen :4202, family :4203.
run-frontend:
	cd frontend && npm install && npm start

# Demo over HTTPS in the LAN (Docker): builds everything, prints the start page address, runs the smoke test.
demo:
	deploy/run-demo.sh

# Same through a public Cloudflare quick tunnel (no certificates on the devices).
demo-tunnel:
	deploy/run-demo.sh tunnel

# Checks the running demo web server (headers, routes, TLS with deploy/ca.crt, WebSocket).
smoke:
	node deploy/smoke-test.mjs --host $$(ipconfig getifaddr en0 2>/dev/null || hostname -I | awk '{print $$1}')

# UC-01 end to end in Chrome against the dev servers (make run-backend + make run-frontend).
e2e:
	cd frontend && npm run e2e

# UC-01 end to end against the running demo (make demo).
e2e-demo:
	cd frontend && npm run e2e:demo

# Download the Polish Vosk model into backend/models/ (needed for LIVE mode only).
download-vosk-model:
	./scripts/download-vosk-model.sh

# Stops the demo containers and deletes the generated deploy/site and deploy/ca.crt (make demo does it on exit).
# Keeps the volumes: the database and Caddy's CA (the certificate installed on the devices stays valid).
clean-demo:
	PATH="$$PATH:/Applications/Docker.app/Contents/Resources/bin" docker compose -f deploy/docker-compose.yml --profile tunnel down
	rm -rf deploy/site deploy/ca.crt

# REPLAY recordings (EV-04 stand-in): synthetic Polish speech of every scenario, backend/recordings/*.wav (macOS).
recordings:
	node scripts/make-demo-recordings.mjs
