# Docker Cheat Sheet — Spring Boot + PostgreSQL

Based on your Ledger Service setup on Ubuntu · Docker + Docker Compose · Java 21 · PostgreSQL 16

This covers the concepts you learned, the commands you'll actually use, and a few important details to avoid losing database data or running outdated code.

## 1. The core concepts

| Term                 | Meaning                                        | Your example                       |
| -------------------- | ---------------------------------------------- | ---------------------------------- |
| Image                | A template used to create containers           | `ledger-service:local`             |
| Container            | An instance of an image                        | `paymentplatform-ledger-service-1` |
| Dockerfile           | Instructions for building an image             | `ledger-service/Dockerfile`        |
| Docker Compose       | Defines and runs multiple services together    | `docker-compose.yml`               |
| Volume               | Persistent storage managed by Docker           | `ledger-data`                      |
| Port mapping         | Connects a host port to a container port       | `8081:8081`                        |
| Environment variable | Configuration supplied to a process            | `SPRING_DATASOURCE_URL`            |
| Build context        | Files Docker can access when building an image | `./ledger-service`                 |

Remember: an image is the template; a container is the instance. A container can be stopped and started again. Removing a container does not automatically remove its image or named volumes.

## 2. Your Dockerfile, summarized

```
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/ledger-service-0.0.1-SNAPSHOT.jar ledger.jar
ENTRYPOINT ["java", "-jar", "ledger.jar"]
```

| Instruction  | Purpose                                                                     |
| ------------ | --------------------------------------------------------------------------- |
| `FROM`       | Selects the base image                                                      |
| `WORKDIR`    | Sets the working directory inside the image/container; creates it if needed |
| `COPY`       | Copies files from the build context into the image                          |
| `ENTRYPOINT` | Defines the main process when the container starts                          |

Your `/app` directory exists inside the container, not in your project directory on Ubuntu. Your JAR is copied there as `/app/ledger.jar`.

Important: your current Dockerfile does not compile Java. Build the JAR first, then build the image.

## 3. Your Compose file, summarized

### PostgreSQL service

```
services:
  db:
    image: postgres:16
    environment:
      POSTGRES_DB: ledger_db
      POSTGRES_USER: ledger_service
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - ledger-data:/var/lib/postgresql/data
      - ./ledger-service/src/test/resources/postgres/init.sql:/docker-entrypoint-initdb.d/01-init.sql:ro
```

- `services` defines the services Compose runs.
- `db` is the service name and internal hostname.
- `image` chooses PostgreSQL 16.
- `environment` configures the initial database setup.
- `ledger-data` stores PostgreSQL data persistently.
- The `init.sql` mount makes your SQL script available inside the container, read-only (`:ro`).

The `healthcheck` uses `pg_isready` to check database readiness. Your application uses `depends_on` with `condition: service_healthy` to wait for that check before starting.

### Spring Boot service

```
  ledger-service:
    image: ledger-service:local
    depends_on:
      db:
        condition: service_healthy
    ports:
      - "8081:8081"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/ledger_db
      SPRING_DATASOURCE_USERNAME: ledger_service
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      SPRING_FLYWAY_PASSWORD: flyway_pass

volumes:
  ledger-data:
```

- `image` selects your application image.
- `depends_on` waits for PostgreSQL's health check.
- `ports` publishes the app to your Ubuntu host.
- `environment` overrides Spring Boot configuration.
- The top-level `volumes` declares the named volume Compose manages.

`SPRING_FLYWAY_PASSWORD` overrides the Flyway password from `application.properties`. For a cleaner setup, you can move it into `.env` later.

## 4. Networking: which `localhost`?

This is one of the most important concepts you learned.

Postman on Ubuntu\
`http://localhost:8081`

Ledger Service container

Port 8081

PostgreSQL container\
`db:5432`

- `localhost` means the machine or network namespace you're currently inside.
- From Ubuntu, `localhost:8081` reaches the published application port.
- From the application container, `localhost` refers to the application container itself.
- `db:5432` works because Compose provides internal DNS using the service name `db`.
- PostgreSQL's port `5432` does not need to be published to your host for the application to connect to it.

The port mapping format is:

```
HOST_PORT:CONTAINER_PORT
```

For your setup, `8081:8081` means host port 8081 forwards to container port 8081.

## 5. Commands you'll use regularly

### Start, stop and inspect

| Command                                                      | What it does                                             |
| ------------------------------------------------------------ | -------------------------------------------------------- |
| `docker compose up -d`                                       | Starts services in the background                        |
| `docker compose ps`                                          | Shows service/container status and published ports       |
| `docker compose logs ledger-service`                         | Shows application logs                                   |
| `docker compose logs -f ledger-service`                      | Follows logs live                                        |
| `docker compose logs db`                                     | Shows PostgreSQL logs                                    |
| `docker compose exec ledger-service sh`                      | Opens a shell inside the running app container           |
| `docker compose exec db psql -U ledger_service -d ledger_db` | Opens a PostgreSQL session                               |
| `docker compose down`                                        | Stops and removes Compose containers and network         |
| `docker compose config`                                      | Validates and renders the resolved Compose configuration |

`-d` means detached mode: run in the background so your terminal returns to you.

To leave a container shell, use `exit`.

### Build and inspect images

| Command                                                 | What it does                         |
| ------------------------------------------------------- | ------------------------------------ |
| `docker images`                                         | Lists local images                   |
| `docker ps`                                             | Lists running containers             |
| `docker ps -a`                                          | Lists running and stopped containers |
| `docker build -t ledger-service:local ./ledger-service` | Builds the app image                 |
| `docker image inspect ledger-service:local`             | Displays image metadata              |

You can also stop and remove an individual container with `docker stop CONTAINER_NAME` and `docker rm CONTAINER_NAME`.

## 6. How to update your application

When you change Java code, the running container does not automatically receive the new code. Your JAR is packaged into the image.

Use this workflow from your repository root:

```
cd ledger-service
mvn clean package
cd ..
docker build -t ledger-service:local ./ledger-service
docker compose up -d
```

This performs three jobs:

1. Compiles and packages your application into a JAR.
2. Builds a new image containing that JAR.
3. Recreates the container as needed to use the updated image.

If the container doesn't behave as expected, check:

```
docker compose ps
docker compose logs --tail=100 ledger-service
```

## 7. Volumes and database safety

Your database uses this mount:

```
- ledger-data:/var/lib/postgresql/data
```

That means PostgreSQL's data is stored in a Docker-managed volume rather than only in the container's writable filesystem.

| Operation                              | What happens to the database data?      |
| -------------------------------------- | --------------------------------------- |
| Stop a container                       | Preserved                               |
| Restart a container                    | Preserved                               |
| `docker compose down`                  | Preserved                               |
| Remove/recreate the database container | Preserved, if the same volume is reused |
| `docker compose down -v`               | Named volume is deleted                 |

Warning: don't run `docker compose down -v` casually. It deletes the Compose-managed named volume and its database data.

One more important detail from your setup: PostgreSQL's initialization scripts run only when the database data directory is empty. Adding or changing the mounted `init.sql` won't automatically update an existing database. Also, `POSTGRES_USER`, `POSTGRES_PASSWORD` and `POSTGRES_DB` don't automatically reconfigure an already-initialized database.

## 8. Environment variables and Git

Your `.env` file contains:

```
DB_PASSWORD=test1yes
```

Compose substitutes `${DB_PASSWORD}` with that value.

Your `.gitignore` should include:

```
.env
```

Useful checks:

```
git status --short
git ls-files .env
git check-ignore -v .env
```

- `git status --short` shows changed and untracked files.
- `git ls-files .env` prints the file if Git tracks it.
- `git check-ignore -v .env` tells you which ignore rule applies.

Commit your Dockerfile, Compose file and appropriate non-secret configuration. Never commit real credentials. If a secret has already been committed, adding it to `.gitignore` doesn't remove it from Git history.

### One improvement for your project

Your Compose file currently hardcodes the Flyway password. You can move it to `.env`:

```
DB_PASSWORD=test1yes
FLYWAY_PASSWORD=flyway_pass
```

Then reference it in Compose:

```
SPRING_FLYWAY_PASSWORD: ${FLYWAY_PASSWORD}
```

Make sure the values match the actual database roles' passwords. These credentials are fine for local learning, but use proper secret management and strong credentials for deployment.

## 9. Does your container run forever?

Not necessarily.

- `docker compose up -d` starts it in the background.
- Closing your terminal does not stop it.
- A container stops if its main process exits.
- A crash can stop the application.
- A host reboot may stop containers unless restart policies and Docker startup are configured appropriately.

To request automatic restarts, you can configure this under each service:

```
restart: unless-stopped
```

This is useful for local development, but it doesn't guarantee the application will always be healthy.

## 10. The complete mental model

Dockerfile

Describes how to build your application image.

Image

Contains your packaged application and the Java runtime it needs.

Container

Runs the application process from the image.

Docker Compose

Connects the application and database, configures their environment, and manages their lifecycle.

Named volume

Keeps database files beyond the lifetime of the container.

The key idea: Docker packages and runs your application; Compose coordinates the services it depends on. You now have the basics to run your Spring Boot application and PostgreSQL together, inspect failures, and update the application image.