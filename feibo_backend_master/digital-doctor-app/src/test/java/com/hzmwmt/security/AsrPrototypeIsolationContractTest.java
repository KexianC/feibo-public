package com.hzmwmt.security;

import com.hzmwmt.domain.controller.asr.AsrController;
import com.hzmwmt.domain.controller.asr.AsrFileController;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.CrossOrigin;

import static org.assertj.core.api.Assertions.assertThat;

class AsrPrototypeIsolationContractTest {

    @Test
    void unsafeServerMicrophonePrototypeRequiresExplicitProfileAndHasNoWildcardCors() {
        assertPrototypeOnly(AsrController.class);
        assertPrototypeOnly(AsrFileController.class);
    }

    private void assertPrototypeOnly(Class<?> controller) {
        Profile profile = controller.getAnnotation(Profile.class);
        assertThat(profile).isNotNull();
        assertThat(profile.value()).containsExactly("asr-prototype");
        assertThat(controller.getAnnotation(CrossOrigin.class)).isNull();
    }
}
