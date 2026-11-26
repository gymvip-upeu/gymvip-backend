package com.gymvip.enterprise;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface UserPreferenceRepository extends JpaRepository<UserPreferenceEntity, UUID> {
}
