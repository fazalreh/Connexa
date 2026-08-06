package com.connexa.api.domain.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UploadSignatureTest {

    private static final String SECRET = "test-signing-secret";

    @Test
    void ordersParametersByNameRegardlessOfInsertionOrder() {
        // Storage recomputes the digest from its own ordering, so a signature that depended
        // on the order this service happened to build the map in would fail intermittently.
        Map<String, String> oneWay = new LinkedHashMap<>();
        oneWay.put("timestamp", "1700000000");
        oneWay.put("public_id", "connexa/events/abc");

        Map<String, String> theOther = new LinkedHashMap<>();
        theOther.put("public_id", "connexa/events/abc");
        theOther.put("timestamp", "1700000000");

        assertThat(UploadSignature.sign(oneWay, SECRET))
                .isEqualTo(UploadSignature.sign(theOther, SECRET));
    }

    @Test
    void joinsParametersInTheAgreedForm() {
        assertThat(UploadSignature.canonicalize(Map.of(
                        "public_id", "connexa/events/abc",
                        "timestamp", "1700000000")))
                .isEqualTo("public_id=connexa/events/abc&timestamp=1700000000");
    }

    @Test
    void omitsBlankValuesBecauseTheClientOmitsThemToo() {
        // A parameter signed but not sent produces a digest storage cannot reproduce.
        assertThat(UploadSignature.canonicalize(Map.of(
                        "public_id", "connexa/events/abc",
                        "folder", "",
                        "timestamp", "1700000000")))
                .isEqualTo("public_id=connexa/events/abc&timestamp=1700000000");
    }

    @Test
    void producesAHexDigest() {
        assertThat(UploadSignature.sign(Map.of("timestamp", "1700000000"), SECRET))
                .matches("[0-9a-f]{40}");
    }

    @Test
    void adifferentSecretProducesADifferentSignature() {
        Map<String, String> parameters = Map.of("timestamp", "1700000000");

        assertThat(UploadSignature.sign(parameters, SECRET))
                .isNotEqualTo(UploadSignature.sign(parameters, "another-secret"));
    }

    @Test
    void changingAnyParameterChangesTheSignature() {
        // This is what stops a client redirecting an authorised upload somewhere else.
        String forOne = UploadSignature.sign(
                Map.of("public_id", "connexa/events/abc", "timestamp", "1700000000"), SECRET);
        String forAnother = UploadSignature.sign(
                Map.of("public_id", "connexa/events/xyz", "timestamp", "1700000000"), SECRET);

        assertThat(forOne).isNotEqualTo(forAnother);
    }

    @Test
    void refusesToSignWithoutASecret() {
        assertThatThrownBy(() -> UploadSignature.sign(Map.of("timestamp", "1"), " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
