.PHONY: build test run-backend

# Build the backend without running tests.
build:
	cd backend && ./mvnw -DskipTests package

# Run all backend tests (unit, contract, ArchUnit) via verify.
test:
	cd backend && ./mvnw verify

# Start the backend with the dev profile.
run-backend:
	cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
