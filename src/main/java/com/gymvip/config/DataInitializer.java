package com.gymvip.config;

import com.gymvip.dto.request.CreatePlanRequest;
import com.gymvip.entity.Account;
import com.gymvip.enums.Role;
import com.gymvip.repository.AccountRepository;
import com.gymvip.repository.PlanRepository;
import com.gymvip.service.PlanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final AccountRepository accountRepository;
    private final PlanRepository planRepository;
    private final PlanService planService;
    private final PasswordEncoder passwordEncoder;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @Value("${app.seed.dev-defaults-enabled:true}")
    private boolean devDefaultsEnabled;

    @Value("${APP_ADMIN_INITIAL_PASSWORD:}")
    private String adminInitialPassword;

    @Value("${APP_GERENTE_INITIAL_PASSWORD:}")
    private String gerenteInitialPassword;

    @Value("${APP_RECEPCION_INITIAL_PASSWORD:}")
    private String recepcionInitialPassword;

    @Value("${APP_SUPER_ADMIN_INITIAL_PASSWORD:}")
    private String superAdminInitialPassword;

    public DataInitializer(AccountRepository accountRepository,
                           PlanRepository planRepository,
                           PlanService planService,
                           PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.planRepository = planRepository;
        this.planService = planService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seedAccounts();
        seedInitialPlans();
    }

    private void seedAccounts() {
        boolean isProd = "prod".equalsIgnoreCase(activeProfile);
        boolean adminExists = accountRepository.existsByEmailIgnoreCase("admin@vipcenterfit.pe");
        boolean gerenteExists = accountRepository.existsByEmailIgnoreCase("gerente@vipcenterfit.pe");
        boolean recepcionExists = accountRepository.existsByEmailIgnoreCase("recepcion@vipcenterfit.pe");

        if (adminExists && gerenteExists && recepcionExists && accountRepository.existsByEmailIgnoreCase("superadmin@gymvip.pe")) {
            log.info("Cuentas base de personal ya aprovisionadas en base de datos.");
            return;
        }

        if (!accountRepository.existsByEmailIgnoreCase("superadmin@gymvip.pe")) {
            String pwd = resolveInitialPassword(superAdminInitialPassword, isProd, "SuperAdmin123!", "APP_SUPER_ADMIN_INITIAL_PASSWORD", "superadmin@gymvip.pe");
            if (pwd != null) {
                accountRepository.save(new Account(
                        UUID.randomUUID(),
                        "superadmin@gymvip.pe",
                        passwordEncoder.encode(pwd),
                        Set.of(Role.SUPER_ADMIN.name()),
                        "ACTIVO",
                        false
                ));
                log.info("Semilla de plataforma creada: superadmin@gymvip.pe (Rol: SUPER_ADMIN / Operador SaaS)");
            }
        }

        if (!adminExists) {
            String pwd = resolveInitialPassword(adminInitialPassword, isProd, "Admin123!", "APP_ADMIN_INITIAL_PASSWORD", "admin@vipcenterfit.pe");
            if (pwd != null) {
                accountRepository.save(new Account(
                        UUID.randomUUID(),
                        "admin@vipcenterfit.pe",
                        passwordEncoder.encode(pwd),
                        Set.of(Role.ADMIN.name()),
                        "ACTIVO",
                        false
                ));
                log.info("Semilla de personal creada: admin@vipcenterfit.pe (Rol: ADMIN / Soporte TI)");
            }
        }

        if (!gerenteExists) {
            String pwd = resolveInitialPassword(gerenteInitialPassword, isProd, "Gerente123!", "APP_GERENTE_INITIAL_PASSWORD", "gerente@vipcenterfit.pe");
            if (pwd != null) {
                accountRepository.save(new Account(
                        UUID.randomUUID(),
                        "gerente@vipcenterfit.pe",
                        passwordEncoder.encode(pwd),
                        Set.of(Role.GERENTE.name()),
                        "ACTIVO",
                        false
                ));
                log.info("Semilla de personal creada: gerente@vipcenterfit.pe (Rol: GERENTE / Dirección y Finanzas)");
            }
        }

        if (!recepcionExists) {
            String pwd = resolveInitialPassword(recepcionInitialPassword, isProd, "Recepcion123!", "APP_RECEPCION_INITIAL_PASSWORD", "recepcion@vipcenterfit.pe");
            if (pwd != null) {
                accountRepository.save(new Account(
                        UUID.randomUUID(),
                        "recepcion@vipcenterfit.pe",
                        passwordEncoder.encode(pwd),
                        Set.of(Role.RECEPCION.name()),
                        "ACTIVO",
                        false
                ));
                log.info("Semilla de personal creada: recepcion@vipcenterfit.pe (Rol: RECEPCION)");
            }
        }
    }

    private String resolveInitialPassword(String configured, boolean isProd, String devFallback, String envName, String email) {
        String candidate = configured != null ? configured.trim() : "";
        if (!isProd && devDefaultsEnabled && candidate.isEmpty()) {
            return devFallback;
        }
        if (candidate.length() < 12 || candidate.equals(devFallback)) {
            if (isProd) {
                log.warn("Cuenta {} ausente en prod y {} no definida con contraseña segura (>=12 caracteres). Se omite provisión automática.", email, envName);
            }
            return null;
        }
        return candidate;
    }

    private void seedInitialPlans() {
        if (planRepository.findAll().isEmpty()) {
            planService.createPlan(
                    new CreatePlanRequest(
                            "Plan VIP Mensual",
                            "Acceso completo a la sala de pesas y máquinas de cardio.",
                            30,
                            new BigDecimal("120.00"),
                            "Acceso ilimitado, casillero de uso diario, hidratación básica"
                    ),
                    "sistema@vipcenterfit.pe"
            );

            planService.createPlan(
                    new CreatePlanRequest(
                            "Plan VIP Trimestral",
                            "Paquete de 3 meses para entrenamiento continuo y resultados visibles.",
                            90,
                            new BigDecimal("320.00"),
                            "Acceso ilimitado, casillero de uso diario, 1 evaluación física mensual"
                    ),
                    "sistema@vipcenterfit.pe"
            );

            planService.createPlan(
                    new CreatePlanRequest(
                            "Plan VIP Semestral",
                            "Medio año con beneficio económico preferencial.",
                            180,
                            new BigDecimal("580.00"),
                            "Acceso ilimitado, toalla de mano, casillero y asesoría de rutina básica"
                    ),
                    "sistema@vipcenterfit.pe"
            );

            planService.createPlan(
                    new CreatePlanRequest(
                            "Plan VIP Anual",
                            "Membresía completa de 12 meses con máximo ahorro.",
                            365,
                            new BigDecimal("990.00"),
                            "Acceso todo el año, casillero exclusivo, opción de congelamiento de hasta 15 días"
                    ),
                    "sistema@vipcenterfit.pe"
            );

            log.info("Semilla creada: 4 planes base iniciales de membresía configurados.");
        }
    }
}
