# ==============================================================================
# GYM VIP (VIP CENTER FIT) — DOCKERFILE MULTI-STAGE BACKEND (RENDER DEPLOYMENT)
# Base: Eclipse Temurin Java 21 LTS (Alpine Linux)
# ==============================================================================

# --- Etapa 1: Compilación de la aplicación ---
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Copiar configuración de Maven Wrapper y dependencias primero para cachear capas
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B

# Copiar el código fuente y compilar el paquete ejecutable
COPY src/ src/
RUN ./mvnw clean package -DskipTests -B

# --- Etapa 2: Imagen liviana de ejecución en producción ---
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Crear usuario y grupo de sistema sin privilegios para seguridad en contenedor
RUN addgroup -S gymvip && adduser -S gymvip -G gymvip

# Copiar el artefacto jar compilado desde la etapa builder
COPY --from=builder /workspace/target/*.jar app.jar
RUN chown -R gymvip:gymvip /app

# Ejecutar como usuario no privilegiado
USER gymvip

# Variables de entorno por defecto (Render inyecta PORT dinámicamente)
ENV PORT=8080
ENV SPRING_PROFILES_ACTIVE=prod
ENV TZ=America/Lima

EXPOSE 8080

# Iniciar Spring Boot con zona horaria America/Lima y mapeo de puerto dinámico
ENTRYPOINT ["sh", "-c", "java -Duser.timezone=America/Lima -Dserver.port=${PORT} -jar app.jar"]
