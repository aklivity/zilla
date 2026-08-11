/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.common.x509;

import static io.aklivity.zilla.runtime.common.x509.X509Fields.ISSUER_DN;
import static io.aklivity.zilla.runtime.common.x509.X509Fields.SAN_DNS;
import static io.aklivity.zilla.runtime.common.x509.X509Fields.SAN_EMAIL;
import static io.aklivity.zilla.runtime.common.x509.X509Fields.SAN_IP;
import static io.aklivity.zilla.runtime.common.x509.X509Fields.SAN_URI;
import static io.aklivity.zilla.runtime.common.x509.X509Fields.SUBJECT_CN;
import static io.aklivity.zilla.runtime.common.x509.X509Fields.SUBJECT_DN;
import static io.aklivity.zilla.runtime.common.x509.X509Fields.X5T_S256;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class X509FieldsTest
{
    // subject CN=platform.example.com, two OU values, and every supported subject alternative name type
    private static final String PLATFORM = """
        -----BEGIN CERTIFICATE-----
        MIIEBjCCAu6gAwIBAgIUcznhiAqbR3wfBoNqZfjUTRNAYzgwDQYJKoZIhvcNAQEL
        BQAwNzELMAkGA1UEBhMCVVMxFDASBgNVBAoMC0V4YW1wbGUgSW5jMRIwEAYDVQQD
        DAlDb21tb24gQ0EwIBcNMjYwODA5MDIyMDQ4WhgPMjEyNjA3MTYwMjIwNDhaMGsx
        CzAJBgNVBAYTAlVTMRQwEgYDVQQKDAtFeGFtcGxlIEluYzERMA8GA1UECwwIUGxh
        dGZvcm0xFDASBgNVBAsMC0VuZ2luZWVyaW5nMR0wGwYDVQQDDBRwbGF0Zm9ybS5l
        eGFtcGxlLmNvbTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAI7yOO4N
        diu8FQ9CTstRJ1O1iOlP+Z/wNaQQT3eRXmSF/w2FHlWE+l4m7+x7IBpIWdf/tA78
        XANicZl6ExL5pJDegp3HzOnO+kQZz7bmI6jdtWmXQD5xaU35ie9wL83xRKcgpbC6
        e7oTmOGIkQlTQwMmIIKqdfmY8BduxXK5WK/B4r3NPUzTopEfT9cgHQLH+NfpdMYK
        o1jfm5kmtG3P5d/yYgfe9Wtt7xtUAMol/ke8GoE6AYCkM54mN6ALCb/itXUzUCiI
        Y3WGN5cq2EZ5gnzJo7yYRGafLeJ3Ujaq8mC2YEMfZLtobq5vX0ClxiIudOfVCMeb
        8B7rHD8Htg6CaBUCAwEAAaOB0zCB0DB5BgNVHREEcjBwghRwbGF0Zm9ybS5leGFt
        cGxlLmNvbYISZXZlbnRzLmV4YW1wbGUuY29thihzcGlmZmU6Ly9leGFtcGxlLmNv
        bS9ucy9wcm9kL3NhL3BsYXRmb3JtgRRwbGF0Zm9ybUBleGFtcGxlLmNvbYcECgEC
        AzATBgNVHSUEDDAKBggrBgEFBQcDAjAdBgNVHQ4EFgQU2Zw2WzXHmWYm9n8gGV1P
        ypiCnYMwHwYDVR0jBBgwFoAUL686n4zISITYaE4EK/jw+nwXakswDQYJKoZIhvcN
        AQELBQADggEBAHbfmNEHlYrtSnhG40gYM+0U9MfNVUK/VgXFyeZGdPd8YQoP3IuB
        /vuxw/Z1Qa+z9abQo/U3wsiXijQAZ/vnZX6qTQALv14fxyGMwN/r5BkklCxZfvGU
        9yrwYz7dvCanEhSh6UotWPQIVS8xO6I+2fx1986lonqikzh0S4YMWVLvcmwxTLJR
        eT+aE8kFuF/sgFz3121+aC+9gOSWkNk4dbajddINuj9wIjtbzh6BhhN2hBEUfhtj
        GkA0EEBoskVzXvM+2e8eppz20mcNY4h8DLIVNPHiqMp41BP0QYBmRYXE308jpkhE
        QLGSKn2820CJCXjmFvO7SDV8KitiYqs+x54=
        -----END CERTIFICATE-----
        """;

    // subject CN=Doe\, John -- an escaped comma inside a single relative distinguished name
    private static final String ESCAPED_COMMA = """
        -----BEGIN CERTIFICATE-----
        MIIC9zCCAd8CFHM54YgKm0d8HwaDamX41E0TQGM5MA0GCSqGSIb3DQEBCwUAMDcx
        CzAJBgNVBAYTAlVTMRQwEgYDVQQKDAtFeGFtcGxlIEluYzESMBAGA1UEAwwJQ29t
        bW9uIENBMCAXDTI2MDgwOTAyMjA0OFoYDzIxMjYwNzE2MDIyMDQ4WjA3MQswCQYD
        VQQGEwJVUzEUMBIGA1UECgwLRXhhbXBsZSBJbmMxEjAQBgNVBAMMCURvZSwgSm9o
        bjCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAKb4/CerEPNnrRRf8CaP
        d9fj8mxFRba/847BVEQY9eV7JALSgPhS2Yki/1knVSRDnwzbiqlc1s6CkD8CAQEM
        7kFbCRtDzMi2bceCVshHqrNoCh32xHhDpkE5OJV+KifWJlFtmRuP5oKydko//aJo
        1sXlWpciZpqwtlfl2VQJQ9lycqsh3WQZNSIo1+XmmD7WBIImL4FaV5NBsytdftHY
        sFz8gnGTXfed4w0a5y0UlcGlH7dMRWuBsIEByAjgk3OVZTs6n7UxpARbfxy4mYf6
        pqPLxwwZ+03l2v2LgHaHmwQtNup8wuubbSd3u9QBguchFoLPVaP2paacqYCpSuBs
        ph0CAwEAATANBgkqhkiG9w0BAQsFAAOCAQEAGEPsKipXgooaGm0gt4c6FluTKSUq
        INXkj1F66FbEb6xReVgNtDCax04xRG01j4MGG++91hFyGp7zzLPKtAdCPWdz8dfZ
        TK2e8Qsn3q5bvYgfo+sbyxUtPh8v35PRh2Mgz5XEIzkgeJVh6YsxfNBAsebrnbiO
        Ph+vCuMNPv8n6v41hysxFFPjCI+uAckSMxoOaB63rOq2deitfKBxxb2E1re7LcdY
        rEKqGQpDLHpuKz7BXmP/jJbjiE6BPvJeOwNDErZWT41klLMINwsq/3NP7G5Tc0pa
        S0gqLVBPwh6dxEG7OKhDSQ+eRmgmQehepr3WC2PuOspJhL8JPo0jZaURWA==
        -----END CERTIFICATE-----
        """;

    // subject CN=alpha+OU=beta -- one multi-valued relative distinguished name
    private static final String MULTI_VALUED_RDN = """
        -----BEGIN CERTIFICATE-----
        MIIDADCCAegCFHM54YgKm0d8HwaDamX41E0TQGM6MA0GCSqGSIb3DQEBCwUAMDcx
        CzAJBgNVBAYTAlVTMRQwEgYDVQQKDAtFeGFtcGxlIEluYzESMBAGA1UEAwwJQ29t
        bW9uIENBMCAXDTI2MDgwOTAyMjA0OFoYDzIxMjYwNzE2MDIyMDQ4WjBAMQswCQYD
        VQQGEwJVUzEUMBIGA1UECgwLRXhhbXBsZSBJbmMxGzALBgNVBAsMBGJldGEwDAYD
        VQQDDAVhbHBoYTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBANA5d0zo
        iOcMY6fai1EsBkBThEXYM7v+B/I4E5oTInJOn3l2nyeH6aGpTLEF+vNW8yJTwKcq
        kf2PtgAbm8LLHS3TnLxcBv5zHaDvopv2EHG1fL4lsNMiC1Uo72RXV0MBOE+kSe/K
        ZD1wJfCvYgaMy3Efu9gc65I+X5lop5HHXjkSKFHLtE0Phm4GgiMfNF9EU51wMu9c
        cbwpdXPT8cOD6EUy8w5J4nh599t+xHRR0IhytVVto1F1NNYBs3x3E1hDPcLi1Qm4
        0zLQOQ6Kw2RBreCOAFNtyRtGRSGZNv1TmM5De/TntRaBNBDVuVtX+DkIkuszoNkd
        gMqKfSgTwui9KJsCAwEAATANBgkqhkiG9w0BAQsFAAOCAQEAGVH7dlB0gevYj4bz
        rAX9lA+7OytzIHCXMnDJSSlvJPHmQiQuR346zwhnPen7hXlc+6+l6nVKC64gVSs+
        /eFcfbyc0Gr/diztvcHXjtgokw+xV0R1/JoVSjqs+Z1i00cS7zIq6toTdnDpd7df
        x006Np04N60F/TaJJmq88+J8b07HVRJjywS65kKKaI5V2t0UNoVc4ZecdSOHefYN
        iLWbQBkVX+RFE2qXy1VGuBMl49Mpve6tHBPbOAzWb885D4NZY9i2yb9oONnfOrcf
        gbc/FBMLKi3JtUtVN+N0CVo5eyjYtri/BWzYcfNoP/oKIkPQbIQ9orGKpvj5EhYu
        ZA1XmQ==
        -----END CERTIFICATE-----
        """;

    @Test
    public void shouldResolveCanonicalSubjectDistinguishedName()
    {
        Map<String, List<String>> fields = X509Fields.resolve(decode(PLATFORM));

        assertThat(fields.get(SUBJECT_DN),
            contains("cn=platform.example.com,ou=engineering,ou=platform,o=example inc,c=us"));
    }

    @Test
    public void shouldResolveCanonicalIssuerDistinguishedName()
    {
        Map<String, List<String>> fields = X509Fields.resolve(decode(PLATFORM));

        assertThat(fields.get(ISSUER_DN), contains("cn=common ca,o=example inc,c=us"));
    }

    @Test
    public void shouldResolveRelativeSubjectFields()
    {
        Map<String, List<String>> fields = X509Fields.resolve(decode(PLATFORM));

        assertThat(fields.get(SUBJECT_CN), contains("platform.example.com"));
        assertThat(fields.get("subject.o"), contains("Example Inc"));
        assertThat(fields.get("subject.c"), contains("US"));
    }

    @Test
    public void shouldResolveRepeatedRelativeSubjectField()
    {
        Map<String, List<String>> fields = X509Fields.resolve(decode(PLATFORM));

        // relative distinguished names are enumerated least significant first
        assertThat(fields.get("subject.ou"), contains("Platform", "Engineering"));
    }

    @Test
    public void shouldResolveRelativeIssuerFields()
    {
        Map<String, List<String>> fields = X509Fields.resolve(decode(PLATFORM));

        assertThat(fields.get("issuer.cn"), contains("Common CA"));
    }

    @Test
    public void shouldResolveSubjectAlternativeNames()
    {
        Map<String, List<String>> fields = X509Fields.resolve(decode(PLATFORM));

        assertThat(fields.get(SAN_DNS), contains("platform.example.com", "events.example.com"));
        assertThat(fields.get(SAN_URI), contains("spiffe://example.com/ns/prod/sa/platform"));
        assertThat(fields.get(SAN_EMAIL), contains("platform@example.com"));
        assertThat(fields.get(SAN_IP), contains("10.1.2.3"));
    }

    @Test
    public void shouldResolveThumbprint()
    {
        Map<String, List<String>> fields = X509Fields.resolve(decode(PLATFORM));

        List<String> thumbprint = fields.get(X5T_S256);

        assertThat(thumbprint, not(nullValue()));
        assertThat(thumbprint.size(), equalTo(1));
        // base64url, unpadded, of a SHA-256 digest
        assertThat(thumbprint.get(0).matches("[A-Za-z0-9_-]{43}"), equalTo(true));
    }

    @Test
    public void shouldOmitAbsentSubjectAlternativeNames()
    {
        Map<String, List<String>> fields = X509Fields.resolve(decode(ESCAPED_COMMA));

        assertThat(fields.get(SAN_DNS), nullValue());
        assertThat(fields.get(SAN_URI), nullValue());
        assertThat(fields.get(SAN_EMAIL), nullValue());
        assertThat(fields.get(SAN_IP), nullValue());
    }

    @Test
    public void shouldResolveCommonNameContainingEscapedComma()
    {
        Map<String, List<String>> fields = X509Fields.resolve(decode(ESCAPED_COMMA));

        assertThat(fields.get(SUBJECT_CN), contains("Doe, John"));
        assertThat(fields.get(SUBJECT_DN), contains("cn=doe\\, john,o=example inc,c=us"));
    }

    @Test
    public void shouldResolveMultiValuedRelativeDistinguishedName()
    {
        Map<String, List<String>> fields = X509Fields.resolve(decode(MULTI_VALUED_RDN));

        assertThat(fields.get(SUBJECT_CN), contains("alpha"));
        assertThat(fields.get("subject.ou"), contains("beta"));
        assertThat(fields.get(SUBJECT_DN), contains("cn=alpha+ou=beta,o=example inc,c=us"));
    }

    @Test
    public void shouldCanonicalizeDistinguishedName()
    {
        String canonical = X509Fields.canonicalName("CN=platform.example.com,OU=Engineering,OU=Platform,O=Example Inc,C=US");

        assertThat(canonical, equalTo("cn=platform.example.com,ou=engineering,ou=platform,o=example inc,c=us"));
    }

    @Test
    public void shouldCanonicalizeDistinguishedNameEqualToResolvedSubject()
    {
        Map<String, List<String>> fields = X509Fields.resolve(decode(PLATFORM));

        String canonical = X509Fields.canonicalName("CN=platform.example.com,OU=Engineering,OU=Platform,O=Example Inc,C=US");

        assertThat(fields.get(SUBJECT_DN), contains(canonical));
    }

    @Test
    public void shouldNotCanonicalizeMalformedDistinguishedName()
    {
        assertThat(X509Fields.canonicalName("not a distinguished name"), nullValue());
    }

    private static X509Certificate decode(
        String encoded)
    {
        X509Certificate certificate = null;

        try
        {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            certificate = (X509Certificate) factory
                .generateCertificate(new ByteArrayInputStream(encoded.getBytes(US_ASCII)));
        }
        catch (Exception ex)
        {
            throw new AssertionError(ex);
        }

        return certificate;
    }
}
