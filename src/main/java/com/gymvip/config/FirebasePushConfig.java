package com.gymvip.config;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

@Configuration
@Conditional(FirebasePushConfig.CredentialsPresent.class)
public class FirebasePushConfig {
    static String credentialFile(Environment env) {
        return env.getProperty("FIREBASE_SERVICE_ACCOUNT_FILE", env.getProperty("GOOGLE_APPLICATION_CREDENTIALS", ""));
    }

    static class CredentialsPresent implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            var env = context.getEnvironment();
            return !env.getProperty("FIREBASE_SERVICE_ACCOUNT_BASE64", "").isBlank() || !credentialFile(env).isBlank();
        }
    }

    @Bean(destroyMethod = "delete")
    FirebaseApp gymVipFirebaseApp(Environment env) throws IOException {
        String encoded = env.getProperty("FIREBASE_SERVICE_ACCOUNT_BASE64", "");
        try (InputStream input = encoded.isBlank()
                ? Files.newInputStream(Path.of(credentialFile(env)))
                : new ByteArrayInputStream(Base64.getDecoder().decode(encoded))) {
            var credentials = ServiceAccountCredentials.fromStream(input);
            String expected = env.getProperty("FIREBASE_PROJECT_ID", "");
            if (!expected.isBlank() && !expected.equals(credentials.getProjectId())) {
                throw new IllegalArgumentException("La cuenta de servicio no pertenece al proyecto Firebase esperado.");
            }
            return FirebaseApp.initializeApp(
                    FirebaseOptions.builder()
                            .setCredentials(credentials)
                            .setProjectId(credentials.getProjectId())
                            .build(),
                    "gymvip-push"
            );
        }
    }

    @Bean
    FirebaseMessaging firebaseMessaging(FirebaseApp gymVipFirebaseApp) {
        return FirebaseMessaging.getInstance(gymVipFirebaseApp);
    }
}
