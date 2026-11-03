package com.hzmwmt.geo.service;

import com.hzmwmt.common.exception.AppException;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeoWebsiteUrlPolicyTest {

    private static final InetAddress PUBLIC_ADDRESS = address(93, 184, 216, 34);

    @Test
    void normalizesPublicDomainWithoutTouchingNetwork() {
        FixedDnsPolicy policy = new FixedDnsPolicy()
                .answer("hospital.example", PUBLIC_ADDRESS);

        GeoWebsiteUrlPolicy.ValidatedUrl result = policy.validate(" hospital.example/departments?q=geo ");

        assertThat(result.normalizedUrl()).isEqualTo("https://hospital.example/departments?q=geo");
        assertThat(result.host()).isEqualTo("hospital.example");
        assertThat(result.domain()).isEqualTo("hospital.example");
        assertThat(result.port()).isEqualTo(443);
        assertThat(result.addresses()).containsExactly(PUBLIC_ADDRESS);
    }

    @Test
    void rejectsPrivateDnsAnswerEvenWhenAnotherAnswerIsPublic() {
        FixedDnsPolicy policy = new FixedDnsPolicy()
                .answer("mixed.example", PUBLIC_ADDRESS, address(10, 20, 30, 40));

        assertThatThrownBy(() -> policy.validate("https://mixed.example"))
                .isInstanceOfSatisfying(AppException.class,
                        error -> assertThat(error.getCode()).isEqualTo("GEO_WEBSITE_PRIVATE_ADDRESS"));
    }

    @Test
    void rejectsLoopbackLinkLocalAndIpLiteralTargets() {
        FixedDnsPolicy policy = new FixedDnsPolicy()
                .answer("loopback.example", address(127, 0, 0, 1))
                .answer("link-local.example", address(169, 254, 1, 2));

        assertPrivateAddress(policy, "https://loopback.example");
        assertPrivateAddress(policy, "https://link-local.example");
        assertThatThrownBy(() -> policy.validate("http://192.168.1.10"))
                .isInstanceOfSatisfying(AppException.class,
                        error -> assertThat(error.getCode()).isEqualTo("PARAM_INVALID"));
    }

    @Test
    void rejectsCredentialsFragmentsAndNonStandardPorts() {
        FixedDnsPolicy policy = new FixedDnsPolicy()
                .answer("hospital.example", PUBLIC_ADDRESS);

        assertThatThrownBy(() -> policy.validate("https://admin:secret@hospital.example/"))
                .isInstanceOfSatisfying(AppException.class,
                        error -> assertThat(error.getCode()).isEqualTo("PARAM_INVALID"));
        assertThatThrownBy(() -> policy.validate("https://hospital.example/#internal"))
                .isInstanceOfSatisfying(AppException.class,
                        error -> assertThat(error.getCode()).isEqualTo("PARAM_INVALID"));
        assertThatThrownBy(() -> policy.validate("https://hospital.example:8443/"))
                .isInstanceOfSatisfying(AppException.class,
                        error -> assertThat(error.getCode()).isEqualTo("GEO_WEBSITE_PORT_REJECTED"));
    }

    @Test
    void allowsOnlyExactOrSubdomainRedirectBoundaries() {
        FixedDnsPolicy policy = new FixedDnsPolicy()
                .answer("www.hospital.example", PUBLIC_ADDRESS)
                .answer("hospital.example", PUBLIC_ADDRESS)
                .answer("news.hospital.example", PUBLIC_ADDRESS)
                .answer("evil-hospital.example", PUBLIC_ADDRESS)
                .answer("hospital.example.evil.test", PUBLIC_ADDRESS);
        GeoWebsiteUrlPolicy.ValidatedUrl origin = policy.validate("https://www.hospital.example");

        assertThat(policy.sameSite(origin, policy.validate("https://hospital.example"))).isTrue();
        assertThat(policy.sameSite(origin, policy.validate("https://news.hospital.example"))).isTrue();
        assertThat(policy.sameSite(origin, policy.validate("https://evil-hospital.example"))).isFalse();
        assertThat(policy.sameSite(origin, policy.validate("https://hospital.example.evil.test"))).isFalse();
    }

    private static void assertPrivateAddress(GeoWebsiteUrlPolicy policy, String url) {
        assertThatThrownBy(() -> policy.validate(url))
                .isInstanceOfSatisfying(AppException.class,
                        error -> assertThat(error.getCode()).isEqualTo("GEO_WEBSITE_PRIVATE_ADDRESS"));
    }

    private static InetAddress address(int a, int b, int c, int d) {
        try {
            return InetAddress.getByAddress(new byte[]{(byte) a, (byte) b, (byte) c, (byte) d});
        } catch (UnknownHostException e) {
            throw new AssertionError(e);
        }
    }

    private static final class FixedDnsPolicy extends GeoWebsiteUrlPolicy {
        private final Map<String, InetAddress[]> answers = new LinkedHashMap<>();

        FixedDnsPolicy answer(String host, InetAddress... addresses) {
            answers.put(host, addresses);
            return this;
        }

        @Override
        protected InetAddress[] resolveHost(String host) throws UnknownHostException {
            InetAddress[] addresses = answers.get(host);
            if (addresses == null) {
                throw new UnknownHostException(host);
            }
            return addresses.clone();
        }
    }
}
