package com.gymvip.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;

import java.io.ByteArrayInputStream;
import java.util.Base64;

@Configuration
public class FirebasePushConfig {
    @Bean
    @ConditionalOnExpression("'${FIREBASE_SERVICE_ACCOUNT_BASE64:}' != ''")
    FirebaseMessaging firebaseMessaging(@Value("${FIREBASE_SERVICE_ACCOUNT_BASE64:}") String encoded) throws Exception {
        byte[] json = Base64.getDecoder().decode(encoded);
        var credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(json));
        FirebaseApp app = FirebaseApp.initializeApp(FirebaseOptions.builder().setCredentials(credentials).build(), "gymvip-push");
        return FirebaseMessaging.getInstance(app);
    }
}
