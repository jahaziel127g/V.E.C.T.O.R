# Project V.E.C.T.O.R Backend Build

JAVA_VERSION=17
APP_NAME=V.E.C.T.O.R
PORT=8080

.PHONY: build run clean test install

install:
	@chmod +x install.sh
	@./install.sh
	@echo "Building $(APP_NAME)..."
	@mvn clean package -DskipTests -q || echo "Maven not found. Install Maven or use: sdk install maven"

run: build
	@echo "Starting $(APP_NAME) on port $(PORT)..."
	@java -jar target/$(APP_NAME)-1.0.0.jar

clean:
	@rm -rf target/
	@echo "Cleaned build artifacts"

test:
	@mvn test || echo "Maven required for tests"

deps:
	@echo "Installing dependencies..."
	@curl -s "https://get.sdkman.io" | bash
	@sdk install java $(JAVA_VERSION)
	@sdk install maven

.DEFAULT_GOAL := build