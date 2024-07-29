By default the app will assume there is a chat model on https://localhost:8000/v1 that exposes an OpenAI endpoint.

If you would like to use Ollama instead, first install/run Ollama on your machine. Then do one of the following:

## Building the app
When building the app, run `./mvnw clean package -DskipTests` (or `quarkus build --no-tests`)

## Running dev mode
When running dev mode, run `./mvnw quarkus:dev` (or `quarkus dev`).

## Running tests
When running tests, run `./mvnw verify` (or `quarkus build --tests `)

## Running the app outside dev mode
If you want to run the app outside dev mode, first build the app as described above, then run `java -jar target/quarkus-app/quarkus-run.jar`