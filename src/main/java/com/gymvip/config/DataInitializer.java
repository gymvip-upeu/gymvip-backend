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

    @Value("${APP_ADMIN_INITIAL_PASSWORD:Admin123!}")
    private String adminInitialPassword;

    @Value("${APP_RECEPCION_INITIAL_PASSWORD:Recepcion123!}")
    private String recepcionInitialPassword;

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
        // En perfil de producción, las cuentas deben ser provisionadas por migración privada o variables seguras
        if ("prod".equalsIgnoreCase(activeProfile) && "Admin123!".equals(adminInitialPassword)) {
            log.warn("Producción detectada con contraseña por defecto. Se omite la provisión automática de cuentas de prueba.");
            return;
        }

        if (!accountRepository.existsByEmailIgnoreCase("admin@vipcenterfit.pe")) {
            Account admin = new Account(
                    UUID.randomUUID(),
                    "admin@vipcenterfit.pe",
                    passwordEncoder.encode(adminInitialPassword),
                    Set.of(Role.ADMIN.name()),
                    "ACTIVO",
                    false
            );
            accountRepository.save(admin);
            log.info("Semilla de personal creada: admin@vipcenterfit.pe (Rol: ADMIN)");
        }

        if (!accountRepository.existsByEmailIgnoreCase("recepcion@vipcenterfit.pe")) {
            Account recepcion = new Account(
                    UUID.randomUUID(),
                    "recepcion@vipcenterfit.pe",
                    passwordEncoder.encode(recepcionInitialPassword),
                    Set.of(Role.RECEPCION.name()),
                    "ACTIVO",
                    false
            );
            accountRepository.save(recepcion);
            log.info("Semilla de personal creada: recepcion@vipcenterfit.pe (Rol: RECEPCION)");
        }
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
