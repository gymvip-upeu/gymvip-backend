package com.gymvip.shared.config;

import com.gymvip.identity.domain.Account;
import com.gymvip.identity.infrastructure.AccountRepository;
import com.gymvip.plans.application.PlanService;
import com.gymvip.plans.infrastructure.PlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final AccountRepository accountRepository;
    private final PlanRepository planRepository;
    private final PlanService planService;
    private final PasswordEncoder passwordEncoder;

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
        if (!accountRepository.existsByEmailIgnoreCase("superadmin@vipcenterfit.pe")) {
            Account superadmin = new Account(
                    "superadmin@vipcenterfit.pe",
                    passwordEncoder.encode("SuperAdmin123!"),
                    Set.of("SUPERADMIN", "ADMIN")
            );
            accountRepository.save(superadmin);
            log.info("Semilla creada: superadmin@vipcenterfit.pe");
        }

        if (!accountRepository.existsByEmailIgnoreCase("admin@vipcenterfit.pe")) {
            Account admin = new Account(
                    "admin@vipcenterfit.pe",
                    passwordEncoder.encode("Admin123!"),
                    Set.of("ADMIN")
            );
            accountRepository.save(admin);
            log.info("Semilla creada: admin@vipcenterfit.pe");
        }

        if (!accountRepository.existsByEmailIgnoreCase("recepcion@vipcenterfit.pe")) {
            Account recepcion = new Account(
                    "recepcion@vipcenterfit.pe",
                    passwordEncoder.encode("Recepcion123!"),
                    Set.of("RECEPCION")
            );
            accountRepository.save(recepcion);
            log.info("Semilla creada: recepcion@vipcenterfit.pe");
        }
    }

    private void seedInitialPlans() {
        if (planRepository.count() == 0) {
            planService.createPlan(
                    "Plan VIP Mensual",
                    "Acceso completo a la sala de pesas y máquinas de cardio.",
                    30,
                    new BigDecimal("120.00"),
                    "Acceso ilimitado, casillero de uso diario, hidratación básica",
                    "sistema@vipcenterfit.pe"
            );

            planService.createPlan(
                    "Plan VIP Trimestral",
                    "Paquete de 3 meses para entrenamiento continuo y resultados visibles.",
                    90,
                    new BigDecimal("320.00"),
                    "Acceso ilimitado, casillero de uso diario, 1 evaluación física mensual",
                    "sistema@vipcenterfit.pe"
            );

            planService.createPlan(
                    "Plan VIP Semestral",
                    "Medio año con beneficio económico preferencial.",
                    180,
                    new BigDecimal("580.00"),
                    "Acceso ilimitado, toalla de mano, casillero y asesoría de rutina básica",
                    "sistema@vipcenterfit.pe"
            );

            planService.createPlan(
                    "Plan VIP Anual",
                    "Membresía completa de 12 meses con máximo ahorro.",
                    365,
                    new BigDecimal("990.00"),
                    "Acceso todo el año, casillero exclusivo, opción de congelamiento de hasta 15 días",
                    "sistema@vipcenterfit.pe"
            );

            log.info("Semilla creada: 4 planes iniciales de membresía configurados.");
        }
    }
}
