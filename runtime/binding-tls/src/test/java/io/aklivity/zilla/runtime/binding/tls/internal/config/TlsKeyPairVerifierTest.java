/*
 * Copyright 2021-2023 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.tls.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.io.ByteArrayInputStream;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import org.junit.Test;

public class TlsKeyPairVerifierTest
{
    private static final String PRIVATE_KEY = """
        -----BEGIN PRIVATE KEY-----
        MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCbKSRbWWxzdJh3
        CzJETA5kLsngah/V83bEPt0AdYpFWZs2BcgPe/ZBR1HAZlyrXn7Hmz5UTlywtmSz
        NgB8QBg4VaG+OLWQIk7TrHqxVPS0CIrFoXpjtHj+u9sL5+hcYscI0D7M+05ObDUm
        nFw5KFuQILme0Prwzwa1IgbQy10mpxJjZZZnkMU9hhzZn+PSce/T3PI0E+7NhhTo
        TO/BV4XL+slIQB82I939HpF09VsKXdlGGI6rU8TUoK4mLtZexcknYDkAiLzOKfe9
        qkPOCJGBsdbxOixGQUDslxK86qyJsQm7Ai2kduyCQj9gfGUkPBR/fkjiUWVuheeK
        GgX7hzwrAgMBAAECggEALWah+UrWxYfJ7xdrG3nnwh/AuXZEH34QdAby8uXx0i4Q
        kQOrEQDUQzCjtrkdB5XsYerGl7OgQqL0H04/YRv+GknQPJFqayTmzOBMvGPgAGf5
        dy+zWMZfmyYLx9WRc6sBk04f+inUtXTLLqOVBrKM7ETvG44Jx3nEfC7bMLA47wyB
        82AFYQzxgfkHCfDgc1igc0oGXxLVH70P9Wh1NMt8I8Kc10aTwfOBAAbw1uE0UgGd
        w4FH/O/50CWc7tbQYvrt6DfwZp7I5TMhg08zATHHicRZMUXpwg1qs3LLh5IU/Tx+
        s6rkTL28L87SjdRdUddY79jdduYDYOzQgklTdAN7hQKBgQDY2gM5HeuDP+DQMYid
        5IIMYIJUe7RJYWO8CyXoDJ1UfuLEHeZNdVU4prnvtVjwY56PMMRPzZ0wj8t7xbvD
        ic+oScRVppRx3qXdCEzMd/QG+ok6X0sJQDkR9x7a7+Hl3I+1v5HiXeqVa2zxc63c
        m3LtiVkxEusHc6p6fnDvNiEzzQKBgQC3LAfFJ2LPGsmd+EpKVqoJ/gK7qYar2yJb
        m42kDdBVebmfkAQxsLDPWutfMjKEhSxz5kjD022Bo0T1jLNLux5yJlwrI3Hwi5ES
        GUVuu9q2wvCjECP5ckukgt6QQ6onHeNu4S24O9x/z9HLlxOFv4fQr8gKqD7eT6bU
        QdekikKn1wKBgEt2+z4qSmZ/mWX6hvejqRpTj6eE8UXELagoHQX3Nd1M1u+3FAmK
        tqCjbPudmZj2ohxktTysanKW6BJnyqMO14RSP5ArC2fhOsmD9O4HJJY1JAJ7XSqX
        /gRhgoxghLWwnxZ513P2iQd8vgn83tlyA3nknXR1h/Ms7nv4eqbUqJNVAoGBAIEZ
        KUjNH5j0OlF6I3INbr6oJmj5bI9HXQnPsp6DlegIaMmLCUm0TIl4fReVG4HHErOF
        BJfwNef+mKWvytZ/RVAStTc6Ph3ZYB+va/5FrDrPXiavQ4uWALYr/o0TA6OaLyeA
        0WZd2mTCpgylp/7GJQTyPz0zo0uwO9axqTiwsZX1AoGARNMUljaCmcYKlUMWssME
        1vomOHwDkTAjcSoG5xmA0DuxglZCcvZQGLH6Xp4y9YGKoq0jauEs/P4tZ35UDBuA
        33+gdzYD5Y1xF2HoEhJam3KglUEJEIGffRgQVcih1xVupUbp7rSliXfcSmAuDoR5
        dQh9097BZt8mQAUwEk6jURo=
        -----END PRIVATE KEY-----
        """;

    private static final String VALID_CERTIFICATE = """
        -----BEGIN CERTIFICATE-----
        MIIDpjCCAo6gAwIBAgIIYM5edJpxrNkwDQYJKoZIhvcNAQELBQAwXjERMA8GA1UE
        AxMIc2VydmVyY2ExFDASBgNVBAsTC0RldmVsb3BtZW50MREwDwYDVQQKEwhBa2xp
        dml0eTETMBEGA1UECBMKQ2FsaWZvcm5pYTELMAkGA1UEBhMCVVMwHhcNMjQwNjI3
        MDk1NzU4WhcNMjkwNjAxMDk1NzU4WjBeMRIwEAYDVQQDEwlsb2NhbGhvc3QxFDAS
        BgNVBAsTC0RldmVsb3BtZW50MRAwDgYDVQQKEwdBa2xpdnR5MRMwEQYDVQQIEwpD
        YWxpZm9ybmlhMQswCQYDVQQGEwJVUzCCASIwDQYJKoZIhvcNAQEBBQADggEPADCC
        AQoCggEBAJspJFtZbHN0mHcLMkRMDmQuyeBqH9XzdsQ+3QB1ikVZmzYFyA979kFH
        UcBmXKtefsebPlROXLC2ZLM2AHxAGDhVob44tZAiTtOserFU9LQIisWhemO0eP67
        2wvn6FxixwjQPsz7Tk5sNSacXDkoW5AguZ7Q+vDPBrUiBtDLXSanEmNllmeQxT2G
        HNmf49Jx79Pc8jQT7s2GFOhM78FXhcv6yUhAHzYj3f0ekXT1Wwpd2UYYjqtTxNSg
        riYu1l7FySdgOQCIvM4p972qQ84IkYGx1vE6LEZBQOyXErzqrImxCbsCLaR27IJC
        P2B8ZSQ8FH9+SOJRZW6F54oaBfuHPCsCAwEAAaNoMGYwHQYDVR0OBBYEFJyQnLBA
        tDXF3aCPrmlzZvSaq0QqMA4GA1UdDwEB/wQEAwIFoDAUBgNVHREEDTALgglsb2Nh
        bGhvc3QwHwYDVR0jBBgwFoAUXkoqFX5WS6ZHxCVDlde+sGKhgyEwDQYJKoZIhvcN
        AQELBQADggEBAEx1WkZ5EEtvtd2BwOEDtyKNKoYmIBvsAWBxDRDgw9zJxK1Cgpv8
        bfIed/G+O5gwAOK/QnidNoYv5ZpE7GH+b1uQy2QhXOG0RZxJj1UVTK+X6rzKUe1d
        +9kCp/EFz3bZAzoJTaGW7FQsUd8Eae+pXOEkPkgY82eHC5hZ37U4PfPYvFea+j4x
        eC1VtOws/2k5pexdl8FR/eC0eQQdoI3FBARzD0rlGlvDR4raqNlrBahyrsT8PmGP
        bQ+xh6fzeFggjTSrhKiKm2vm3dQKP+eKbihYsZQjlPiE6gEp/pTtV7TYVoX+s+yZ
        lUdAET/1jqTLi2Gd7UuYQN+mlTSoKEpT4bk=
        -----END CERTIFICATE-----
        """;

    private static final String INVALID_CERTIFICATE = """
        -----BEGIN CERTIFICATE-----
        MIIDpjCCAo6gAwIBAgIIJWpr8WDPucUwDQYJKoZIhvcNAQELBQAwXjERMA8GA1UE
        AxMIc2VydmVyY2ExFDASBgNVBAsTC0RldmVsb3BtZW50MREwDwYDVQQKEwhBa2xp
        dml0eTETMBEGA1UECBMKQ2FsaWZvcm5pYTELMAkGA1UEBhMCVVMwHhcNMjQwNjI3
        MTA1OTQ5WhcNMjkwNjAxMTA1OTQ5WjBeMRIwEAYDVQQDEwlsb2NhbGhvc3QxFDAS
        BgNVBAsTC0RldmVsb3BtZW50MRAwDgYDVQQKEwdBa2xpdnR5MRMwEQYDVQQIEwpD
        YWxpZm9ybmlhMQswCQYDVQQGEwJVUzCCASIwDQYJKoZIhvcNAQEBBQADggEPADCC
        AQoCggEBAMPa6QJuljN9vVeBHzb2sB+YgL7pFPI9tL24jaB3mlV4PvXwQ2XoZ7Oy
        gR4I23iU9CfvvhHSiI2+9nAGHZgT74GZDaIiTxZNearQ+4aGRhAAENHXRb7YD6gp
        FiIjZ5SWf2+yfcTIPPi1Sp+DLp9L2FiCgCtghaDBc5xdieC24zhq3Rp56tsaQGen
        sEK4qZ+9bAevD7l59FWat7Yts5p/emu3+UNAka+n2zVz+BF0uc9he4NHgHMvd0uq
        GlnTKWQ3zrQ+LUD7nOOq6db2Pb2+FTg+4+0y/hdzwFkuNL9OOmWvsSOPUIJIBpyR
        gwi0+I+MWODoUPYJE1yLIlGJ5t7SWDsCAwEAAaNoMGYwHQYDVR0OBBYEFD2P04GZ
        l36DmMNQmz7auMC7LStQMA4GA1UdDwEB/wQEAwIFoDAUBgNVHREEDTALgglsb2Nh
        bGhvc3QwHwYDVR0jBBgwFoAU4ebi5t0zi0JyZI1SdZrrG9m6CIwwDQYJKoZIhvcN
        AQELBQADggEBAFr0mzsLaa64aO10HnRpU9ppFkRiFiu3peaqrSZd/1mHxacwPTEr
        c4QtaNhB1sVu1zVlzKPQYJ/yRsUD+wSwURud8syEjeieIXu3mytdQMQN/7hIE3KL
        PO38SUk3pN8GU7auvuysT4/xbnZ7J+55UMCKkjcVO17NwVWXT9BbIKmHScLwCuB+
        +5LG02GLU6IWzrWcCsmsjbMiT+xntAHXzJChwx5abuBpg8hHN3Yaz7/K9E1dVzop
        ZhqL8TG4TlhXtUJjd8VxAroyMi0VZSVHTZoqz2Zmkw/CT30rRT7E/I1zy2ZYUYqS
        kuIWfzOfqCZSmGeqzuSE996ndM+FlQ6r0Zs=
        -----END CERTIFICATE-----
        """;

    private static final char[] PASSWORD = "generated".toCharArray();

    @Test
    public void shouldVerifyValid() throws Exception
    {
        // GIVEN
        TlsKeyPairVerifier verifier = new TlsKeyPairVerifier();
        KeyStore.PrivateKeyEntry entry = privateKeyEntry(PRIVATE_KEY, VALID_CERTIFICATE, PASSWORD);

        // WHEN
        boolean valid = verifier.verify(entry);

        // THEN
        assertThat(valid, equalTo(true));
    }

    @Test
    public void shouldVerifyInvalid() throws Exception
    {
        // GIVEN
        TlsKeyPairVerifier verifier = new TlsKeyPairVerifier();
        KeyStore.PrivateKeyEntry entry = privateKeyEntry(PRIVATE_KEY, INVALID_CERTIFICATE, PASSWORD);

        // WHEN
        boolean valid = verifier.verify(entry);

        // THEN
        assertThat(valid, equalTo(false));
    }

    private static KeyStore.PrivateKeyEntry privateKeyEntry(
        String privateKeyPem,
        String certificatePem,
        char[] password) throws Exception
    {
        PrivateKey privateKey = privateKeyFromPem(privateKeyPem);
        X509Certificate certificate = certificateFromPem(certificatePem);
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, password);
        X509Certificate[] certChain = {certificate};
        keyStore.setKeyEntry("localhost", privateKey, password, certChain);
        return (KeyStore.PrivateKeyEntry) keyStore.getEntry("localhost", new KeyStore.PasswordProtection(password));
    }

    private static PrivateKey privateKeyFromPem(
        String pem) throws Exception
    {
        String privateKeyPem = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(privateKeyPem);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(keySpec);
    }

    private static X509Certificate certificateFromPem(
        String pem) throws Exception
    {
        String certificatePem = pem
            .replace("-----BEGIN CERTIFICATE-----", "")
            .replace("-----END CERTIFICATE-----", "")
            .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(certificatePem);
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(decoded));
    }
}
