.PHONY: build test run-backend run-frontend download-vosk-model

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

# Download the Polish Vosk model into backend/models/ (needed for LIVE mode only).
download-vosk-model:
	./scripts/download-vosk-model.sh
