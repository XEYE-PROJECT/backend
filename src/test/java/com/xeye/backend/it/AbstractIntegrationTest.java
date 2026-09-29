package com.xeye.backend.it;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.MariaDBContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Base de los tests de integración ({@code *IT.java}, los ejecuta failsafe en {@code mvn verify}):
 * la aplicación entera (perfil {@code dev}: training mock, búsqueda log, admin sembrado) sobre
 * una MariaDB real de Testcontainers con las migraciones de Flyway aplicadas. Un solo contenedor
 * para toda la suite (patrón singleton) y un solo contexto de Spring: cada clase añade sus
 * peticiones HTTP reales contra el puerto aleatorio.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
public abstract class AbstractIntegrationTest {

    /** Mismo secreto que los tests envían en {@code X-Webhook-Token} / {@code X-Internal-Token}. */
    protected static final String WEBHOOK_SECRET = "it-webhook-secret-0123456789abcdef0123456789";
    protected static final String INTERNAL_TOKEN = "it-internal-token-0123456789abcdef0123456789";
    protected static final String ADMIN_EMAIL = "admin@xeye.local";
    protected static final String ADMIN_PASSWORD = "admin1234";

    /** Misma imagen que la producción (docker-compose de xeye-infra). */
    static final MariaDBContainer MARIADB = new MariaDBContainer("mariadb:10.11")
            .withDatabaseName("xeye_it")
            .withUsername("xeye")
            .withPassword("xeye");

    static {
        MARIADB.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        // El driver de la app es mysql-connector-j: URL jdbc:mysql, no la jdbc:mariadb del contenedor.
        registry.add("spring.datasource.url", () -> "jdbc:mysql://" + MARIADB.getHost() + ":"
                + MARIADB.getMappedPort(3306) + "/" + MARIADB.getDatabaseName()
                + "?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC");
        registry.add("spring.datasource.username", MARIADB::getUsername);
        registry.add("spring.datasource.password", MARIADB::getPassword);
        registry.add("xeye.training.webhook-secret", () -> WEBHOOK_SECRET);
        registry.add("xeye.search.internal-token", () -> INTERNAL_TOKEN);
        // Sin esperas artificiales del launcher mock ni ruido de SQL en la salida de los tests.
        registry.add("xeye.training.mock-delay-ms", () -> "50");
        registry.add("spring.jpa.show-sql", () -> "false");
        // Los tests hacen decenas de logins desde 127.0.0.1: el límite por IP de producción (10/min) no aplica.
        registry.add("xeye.auth.rate-limit.login-per-minute", () -> "10000");
        registry.add("xeye.auth.rate-limit.register-per-minute", () -> "10000");
    }

    @LocalServerPort
    protected int port;

    protected final ObjectMapper json = new ObjectMapper();

    /** Cliente que no lanza en 4xx/5xx: los tests afirman sobre el estado y el cuerpo. */
    protected RestClient http() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }

    protected Response get(String path, String bearer) {
        return exchange("GET", path, null, bearer, Map.of());
    }

    protected Response post(String path, Object body, String bearer) {
        return exchange("POST", path, body, bearer, Map.of());
    }

    protected Response exchange(String method, String path, Object body, String bearer, Map<String, String> headers) {
        RestClient.RequestBodySpec spec = http().method(org.springframework.http.HttpMethod.valueOf(method)).uri(path)
                .accept(MediaType.APPLICATION_JSON);
        if (bearer != null) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
        }
        headers.forEach(spec::header);
        if (body != null) {
            spec = spec.contentType(MediaType.APPLICATION_JSON);
            spec.body(body instanceof String raw ? raw : json.writeValueAsString(body));
        }
        return spec.exchange((request, response) -> {
            String text = new String(response.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            return new Response(response.getStatusCode().value(), response.getHeaders(),
                    text.isBlank() ? null : json.readTree(text));
        });
    }

    private static String adminToken;

    /** Un solo login de admin para toda la suite (el JWT dura 60 min). */
    protected synchronized String loginAsAdmin() {
        if (adminToken == null) {
            adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        }
        return adminToken;
    }

    protected String login(String email, String password) {
        Response response = post("/auth/login", Map.of("email", email, "password", password), null);
        if (response.status() != 200 || response.body() == null || !response.body().has("token")) {
            throw new IllegalStateException("Login failed for " + email + ": " + response.status() + " " + response.body());
        }
        return response.body().get("token").asString();
    }

    /** Respuesta ya leída: estado, cabeceras y cuerpo JSON (null si vacío). */
    protected record Response(int status, HttpHeaders headers, JsonNode body) {
        String code() {
            return body == null || body.get("code") == null ? null : body.get("code").asString();
        }
    }
}
