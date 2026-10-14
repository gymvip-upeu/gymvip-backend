package com.gymvip.shared.config;

import com.gymvip.identity.application.port.out.AccountPersistencePort;
import com.gymvip.identity.domain.model.Account;
import com.gymvip.plans.application.port.in.ManagePlanUseCase;
import com.gymvip.plans.application.port.out.PlanPersistencePort;
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

    private final AccountPersistencePort accountPersistencePort;
    private final PlanPersistencePort planPersistencePort;
    private final ManagePlanUseCase managePlanUseCase;
    private final PasswordEncoder passwordEncoder;

    @Value("${APP_ADMIN_INITIAL_PASSWORD:Admin123!}")
    private String adminInitialPassword;

    @Value("${APP_RECEPCION_INITIAL_PASSWORD:Recepcion123!}")
    private String recepcionInitialPassword;

    public DataInitializer(AccountPersistencePort accountPersistencePort,
                           PlanPersistencePort planPersistencePort,
                           ManagePlanUseCase managePlanUseCase,
                           PasswordEncoder passwordEncoder) {
        this.accountPersistencePort = accountPersistencePort;
        this.planPersistencePort = planPersistencePort;
        this.managePlanUseCase = managePlanUseCase;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seedAccounts();
        seedInitialPlans();
    }

    private void seedAccounts() {
        if (!accountPersistencePort.existsByEmailIgnoreCase("superadmin@vipcenterfit.pe")) {
            Account superadmin = new Account(
                    UUID.randomUUID(),
                    "superadmin@vipcenterfit.pe",
                    passwordEncoder.encode(adminInitialPassword),
                    Set.of("SUPERADMIN", "ADMIN"),
                    "ACTIVO",
                    false
            );
            accountPersistencePort.save(superadmin);
            log.info("Semilla creada: superadmin@vipcenterfit.pe");
        }

        if (!accountPersistencePort.existsByEmailIgnoreCase("admin@vipcenterfit.pe")) {
            Account admin = new Account(
                    UUID.randomUUID(),
                    "admin@vipcenterfit.pe",
                    passwordEncoder.encode(adminInitialPassword),
                    Set.of("ADMIN"),
                    "ACTIVO",
                    false
            );
            accountPersistencePort.save(admin);
            log.info("Semilla creada: admin@vipcenterfit.pe");
        }

        if (!accountPersistencePort.existsByEmailIgnoreCase("recepcion@vipcenterfit.pe")) {
            Account recepcion = new Account(
                    UUID.randomUUID(),
                    "recepcion@vipcenterfit.pe",
                    passwordEncoder.encode(recepcionInitialPassword),
                    Set.of("RECEPCION"),
                    "ACTIVO",
                    false
            );
            accountPersistencePort.save(recepcion);
            log.info("Semilla creada: recepcion@vipcenterfit.pe");
        }
    }

    private void seedInitialPlans() {
        if (planPersistencePort.findAll(null).isEmpty()) {
            managePlanUseCase.createPlan(
                    new ManagePlanUseCase.CreatePlanCommand(
                            "Plan VIP Mensual",
                            "Acceso completo a la sala de pesas y máquinas de cardio.",
                            30,
                            new BigDecimal("120.00"),
                            "Acceso ilimitado, casillero de uso diario, hidratación básica"
                    ),
                    "sistema@vipcenterfit.pe"
            );

            managePlanUseCase.createPlan(
                    new ManagePlanUseCase.CreatePlanCommand(
                            "Plan VIP Trimestral",
                            "Paquete de 3 meses para entrenamiento continuo y resultados visibles.",
                            90,
                            new BigDecimal("320.00"),
                            "Acceso ilimitado, casillero de uso diario, 1 evaluación física mensual"
                    ),
                    "sistema@vipcenterfit.pe"
            );

            managePlanUseCase.createPlan(
                    new ManagePlanUseCase.CreatePlanCommand(
                            "Plan VIP Semestral",
                            "Medio año con beneficio económico preferencial.",
                            180,
                            new BigDecimal("580.00"),
                            "Acceso ilimitado, toalla de mano, casillero y asesoría de rutina básica"
                    ),
                    "sistema@vipcenterfit.pe"
            );

            managePlanUseCase.createPlan(
                    new ManagePlanUseCase.CreatePlanCommand(
                            "Plan VIP Anual",
                            "Membresía completa de 12 meses con máximo ahorro.",
                            365,
                            new BigDecimal("990.00"),
                            "Acceso todo el año, casillero exclusivo, opción de congelamiento de hasta 15 días"
                    ),
                    "sistema@vipcenterfit.pe"
            );

            log.info("Semilla creada: 4 planes iniciales de membresía configurados.");
        }
    }
}
