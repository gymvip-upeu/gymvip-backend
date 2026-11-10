import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymvip.GymVipApplication;
import com.gymvip.catalog.CatalogService;
import com.gymvip.catalog.CatalogDtos.*;
import com.gymvip.dto.request.*;
import com.gymvip.service.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.List;

/** Lanzador manual de QA. No forma parte del JAR ni de la aplicación distribuida. */
public class QaBackend {
    private static final String URL = "jdbc:postgresql://127.0.0.1:15432/gymvip_checks_jorge";
    private static final String SCHEMA = "gymvip_test_jorge";
    private static String actor;
    public static void main(String[] args) throws Exception {
        if (!URL.equals(System.getenv("TEST_DB_JDBC_URL")) || !SCHEMA.equals(System.getenv("TEST_DB_SCHEMA")))
            throw new IllegalStateException("QA solo admite la base y el esquema locales aislados.");
        if (args.length != 1) throw new IllegalArgumentException("Indica el archivo privado de credenciales QA generadas.");
        System.setProperty("spring.config.import", "optional:classpath:/qa-no-env.properties");
        System.setProperty("spring.profiles.active", "dev");
        System.setProperty("server.port", "8082");
        System.setProperty("server.address", "127.0.0.1");
        System.setProperty("spring.datasource.url", URL);
        System.setProperty("spring.datasource.username", Objects.requireNonNull(System.getenv("TEST_DB_USERNAME")));
        System.setProperty("spring.datasource.password", Objects.requireNonNull(System.getenv("TEST_DB_PASSWORD")));
        System.setProperty("spring.datasource.hikari.schema", SCHEMA);
        System.setProperty("spring.jpa.properties.hibernate.default_schema", SCHEMA);
        System.setProperty("spring.flyway.default-schema", SCHEMA);
        System.setProperty("spring.flyway.schemas", SCHEMA);
        System.setProperty("jwt.secret", "QA-EFIMERO-" + UUID.randomUUID() + UUID.randomUUID());
        var ctx = SpringApplication.run(GymVipApplication.class);
        var jdbc = ctx.getBean(JdbcTemplate.class);
        if (!SCHEMA.equals(jdbc.queryForObject("SELECT current_schema()", String.class))) {
            ctx.close(); throw new IllegalStateException("Esquema de QA incorrecto.");
        }
        if (Files.exists(Path.of(args[0]))) {
            var saved = ctx.getBean(ObjectMapper.class).readTree(Path.of(args[0]).toFile());
            for (String key : List.of("vigente", "proxima", "vencida", "sinMembresia")) {
                var item = saved.get(key);
                var id = UUID.fromString(item.get("clientId").asText());
                if (!ctx.getBean(ClientService.class).getClientById(id).lastName().endsWith("QA") ||
                        !ctx.getBean(MemberWorkspaceService.class).ownClient(item.get("email").asText()).equals(id)) {
                    ctx.close(); throw new IllegalStateException("El archivo no corresponde a escenarios QA.");
                }
            }
            System.out.println("QA LISTO: escenarios aislados existentes verificados, sin duplicarlos.");
            return;
        }
        jdbc.update("UPDATE reminder_configurations SET enabled=false");
        actor = "operador-qa-" + UUID.randomUUID() + "@example.test";
        ctx.getBean(com.gymvip.repository.AccountRepository.class).saveAndFlush(new com.gymvip.entity.Account(UUID.randomUUID(), actor,
                ctx.getBean(org.springframework.security.crypto.password.PasswordEncoder.class).encode(UUID.randomUUID().toString()),
                Set.of("ADMIN"), "ACTIVO", false));
        var plans = ctx.getBean(PlanService.class);
        var plan = plans.listPlans(null).stream().filter(p -> p.name().equals("VIP Mensual QA")).findFirst()
                .orElseGet(() -> plans.createPlan(new CreatePlanRequest("VIP Mensual QA", "Escenario visual aislado", 30,
                        new BigDecimal("99.90"), "Sala de musculación", "Renovación presencial"), actor));
        var output = new LinkedHashMap<String, Object>();
        output.put("vigente", member(ctx, plan.id(), "Valeria", "Rojas QA", 0, true));
        output.put("proxima", member(ctx, plan.id(), "Lucía", "Paredes QA", -27, false));
        output.put("vencida", member(ctx, plan.id(), "Diego", "Torres QA", -35, false));
        output.put("sinMembresia", member(ctx, null, "Ana", "García QA", 0, false));
        var product = ctx.getBean(CatalogService.class).saveSupplement(null,
                new SupplementInput("Whey Protein Isolate · QA", "VIP QA " + UUID.randomUUID().toString().substring(0, 6), "PROTEINA", "Pote", "900 g", "Vainilla",
                        "Producto ficticio para revisar la interfaz con el backend real.", "Información de prueba",
                        new BigDecimal("189.90"), "DISPONIBLE", true, true, null, List.of(), "upload:0"),
                List.of(photo()), actor);
        var today = LocalDate.now(ZoneId.of("America/Lima"));
        ctx.getBean(CatalogService.class).savePromotion(null, new PromotionInput(product.id(), "Impulso VIP · QA",
                "Promoción de prueba", "Solo escenario QA, sin compra en la app", new BigDecimal("159.90"), today,
                today.plusDays(10), true, true, null, product.version()), actor);
        output.put("productoId", product.id().toString());
        ctx.getBean(ObjectMapper.class).writeValue(Path.of(args[0]).toFile(), output);
        System.out.println("QA LISTO: puerto 8082, base y esquema aislados; credenciales fuera del log.");
    }
    private static Map<String, Object> member(ConfigurableApplicationContext ctx, UUID plan, String first, String last,
                                               int offset, boolean primerCambio) {
        var workspace = ctx.getBean(MemberWorkspaceService.class);
        var request = new RegisterClientRequest(first, last, LocalDate.of(1995, 5, 15), "PE", "DNI",
                String.valueOf(10000000 + new java.security.SecureRandom().nextInt(89999999)), null, "qa@example.test");
        var member = workspace.register(new MemberWorkspaceService.Registration(request, "Escenario QA aislado", false), actor);
        var credentials = workspace.provision(member.client().id(), actor);
        String password = "Qa2026!" + UUID.randomUUID().toString();
        if (!primerCambio) workspace.changePassword(credentials.username(),
                new MemberWorkspaceService.PasswordChange(credentials.temporaryPassword(), password));
        if (plan != null) {
            var memberships = ctx.getBean(MembershipService.class);
            var operation = MembershipQuoteRequest.Operation.ENROLL;
            var quote = memberships.quote(new MembershipQuoteRequest(member.client().id(), plan, operation));
            var sale = memberships.sell(new MembershipSaleRequest(member.client().id(), plan, operation, quote.fingerprint(),
                    quote.pricePen(), "YAPE", "QA-LOCAL", "Pago ficticio en base aislada", null), UUID.randomUUID().toString(), actor);
            if (offset != 0) {
                var start = LocalDate.now(ZoneId.of("America/Lima")).plusDays(offset);
                var end = start.plusDays(30);
                var jdbc = ctx.getBean(JdbcTemplate.class);
                jdbc.update("UPDATE membership_periods SET start_date=?,end_date=? WHERE id=?", start, end, sale.periodId());
                jdbc.update("UPDATE memberships SET start_date=?,end_date=? WHERE id=?", start, end, sale.membershipId());
            }
        }
        return Map.of("email", credentials.username(), "temporal", credentials.temporaryPassword(), "password", password,
                "clientId", member.client().id().toString(), "primerCambio", primerCambio);
    }
    private static MockMultipartFile photo() throws Exception {
        var image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try {
            g.setColor(new Color(0x151922)); g.fillRect(0, 0, 256, 256);
            g.setColor(new Color(0x343945)); g.fillRoundRect(77, 50, 102, 160, 18, 18);
            g.setColor(new Color(0xE5C158)); g.fillRoundRect(85, 100, 86, 55, 8, 8);
            g.setColor(new Color(0x151922)); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26)); g.drawString("QA", 108, 136);
        } finally { g.dispose(); }
        var bytes = new ByteArrayOutputStream(); ImageIO.write(image, "png", bytes);
        return new MockMultipartFile("images", "producto-qa.png", "image/png", bytes.toByteArray());
    }
}
