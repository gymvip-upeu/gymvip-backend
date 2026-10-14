package com.gymvip.clients.application.port.in;

import com.gymvip.clients.domain.model.Client;
import java.time.LocalDate;

public interface RegisterClientUseCase {

    Client registerClient(RegisterClientCommand command, String actorEmail, String clientIp);

    record RegisterClientCommand(
            String firstName,
            String lastName,
            LocalDate birthDate,
            String countryCode,
            String documentType,
            String documentNumber,
            String phone,
            String email
    ) {}
}
