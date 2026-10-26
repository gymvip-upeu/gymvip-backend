package com.gymvip.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IsolationGuardTest {
    @Test void sharedEmptyAndOtherDeveloperSchemasStopBeforeMigration() {
        for(String schema:new String[]{"public","","gymvip_test","gymvip_test_alexander"}) {
            var env=new MockEnvironment().withProperty("TEST_DB_SCHEMA",schema);
            var flyway=mock(Flyway.class); var source=mock(DataSource.class);
            assertThrows(IllegalStateException.class,()->new IsolatedDatabaseTestConfig().isolatedTestMigration(env,source).migrate(flyway));
            verifyNoInteractions(flyway,source);
        }
    }
}
