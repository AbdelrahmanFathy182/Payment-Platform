# Maven Wrapper (`mvnw`) Cheat Sheet

The Maven Wrapper lets the project use its own specified Maven version instead of relying on whatever Maven version is installed globally on your machine.

For this project, prefer `./mvnw` over `mvn`.

---

## 1. Check if the Wrapper Exists

From the project root:

```bash
ls -la
```

You should see something like:

```text
mvnw
mvnw.cmd
.mvn/
pom.xml
```

On Linux/macOS, use:

```bash
./mvnw
```

On Windows:

```cmd
mvnw.cmd
```

---

## 2. Make `mvnw` Executable

On Linux:

```bash
chmod +x mvnw
```

You usually only need to do this once after cloning or creating the project.

Then verify:

```bash
ls -l mvnw
```

You should see executable permissions, for example:

```text
-rwxr-xr-x ... mvnw
```

---

## 3. Run the Spring Boot Application

```bash
./mvnw spring-boot:run
```

This is the normal development command for the Payments Platform.

---

## 4. Clean the Build

```bash
./mvnw clean
```

Deletes the generated `target/` directory.

Useful when you suspect stale compiled files or strange build behavior.

Example:

```bash
./mvnw clean
./mvnw spring-boot:run
```

---

## 5. Compile

```bash
./mvnw compile
```

Compiles:

```text
src/main/java/
```

into:

```text
target/classes/
```

---

## 6. Run Tests

```bash
./mvnw test
```

Compiles and runs the project's tests.

---

## 7. Build the Application

```bash
./mvnw package
```

Creates the packaged JAR under:

```text
target/
```

For a completely fresh build:

```bash
./mvnw clean package
```

---

## 8. Build Without Running Tests

```bash
./mvnw package -DskipTests
```

Useful when you specifically need a package but don't want to execute the tests.

Prefer this over:

```bash
./mvnw package -Dmaven.test.skip=true
```

because `-DskipTests` still compiles the tests.

---

## 9. Spring Boot Debug Mode

To see Spring Boot's auto-configuration condition report:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--debug"
```

Useful when investigating:

- auto-configuration
- beans
- Spring configuration
- why something was or wasn't configured

---

## 10. Dependency Tree

```bash
./mvnw dependency:tree
```

Useful for investigating:

- what dependencies your project has
- transitive dependencies
- dependency conflicts
- why a particular library is on the classpath

Example:

```bash
./mvnw dependency:tree | grep spring-web
```

---

## 11. Update Dependencies

```bash
./mvnw clean package -U
```

`-U` tells Maven to check for updated dependencies.

You normally don't need this for everyday development.

---

## 12. Run the Packaged JAR

After:

```bash
./mvnw clean package
```

run the generated JAR:

```bash
java -jar target/<application-name>.jar
```

For example:

```bash
java -jar target/psp-simulator-0.0.1-SNAPSHOT.jar
```

---

# Common Workflow

### Normal development

```bash
./mvnw spring-boot:run
```

### Something behaves strangely

```bash
./mvnw clean
./mvnw spring-boot:run
```

### Run tests

```bash
./mvnw test
```

### Fresh build

```bash
./mvnw clean package
```

### Investigate Spring

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--debug"
```

---

# Maven Wrapper vs Maven

### Maven installed globally

```bash
mvn spring-boot:run
```

Uses the Maven installation available on your machine.

### Maven Wrapper

```bash
./mvnw spring-boot:run
```

Uses the Maven version configured by the project.

For a project that you want other developers to clone and build consistently, the Wrapper is generally preferable.

---

# Quick Reference

```bash
# Make wrapper executable
chmod +x mvnw

# Run application
./mvnw spring-boot:run

# Clean
./mvnw clean

# Compile
./mvnw compile

# Test
./mvnw test

# Build JAR
./mvnw package

# Clean + build
./mvnw clean package

# Build without running tests
./mvnw package -DskipTests

# Dependency tree
./mvnw dependency:tree

# Spring Boot debug
./mvnw spring-boot:run -Dspring-boot.run.arguments="--debug"

# Run packaged JAR
java -jar target/<application>.jar
```

## The commands to remember first

```bash
chmod +x mvnw
./mvnw spring-boot:run
./mvnw test
./mvnw clean
./mvnw clean package
```