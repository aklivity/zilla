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
    private static final String RSA_PRIVATE_KEY = """
        -----BEGIN PRIVATE KEY-----
        MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDp62LciNw3yMp9
        0jeeGO+kdGmqZUQqRFZlckggahlxZnLfvckSpdw18+FOvybX+T4RT5KsCwWgl6eM
        DnyDZYnfK0mIk5TW6gX0oNOB/uR4n9DauN2+owlC1144nWauH0xi7clR0OU/MHPY
        n+36bSIYLzOyxJYvs0fWU5j+wkl62nUo3yiII+h+tPhYmWCBmSDhXCD0BIoHzCYP
        A5+c60uws9tSuzy2l8qVa41HDOdIru79GVoYP8jXJoGugoQ/uJBLJd1dCh2Egfi9
        TOJZafR2hWkdY6wzfaC68fWhS1GAnzjChoVCa0P6nUNYkj7BH4MDoHNUkrurU6zN
        sG7gK1lLAgMBAAECggEAT7LETzFOHq+J1k6eZn5Gf/it28F+9QutiAjk7C5aFtn5
        /6NQ88qQ+czrEgJswJ8J96nt5jInK60gB8cTw00AKYo9Fox55LN9bfixt5PZ0gNZ
        jHv6CS7RQ/XPA2kVh/Pf+cDcm8SZpuriPYdX9L/kIutKKPlz1jK2Ih7/fKVDldhb
        xffwdxqmexnLwNHseNevQbhZKjjllLWZzn78xaSaHghN8fc5TF7SsgiqYGK079bG
        nC7luO1t5XqQ85nql9W2ddpYP5+AKNDAatmIcJH9Kg9auuvB3qJtRFOTmcyphLo4
        EtHjn8Q1Qh9zj9se0oAxdWYYqj86XvnUgq2VPlfx2QKBgQD8sXLr6/M8rNo2w+8f
        qQ6u24+/2Xq/JwwZHU5C7Jpfl/cvabxgP8W9seD5dBI+jiUyFAM1fk/baYKmr1JE
        hHAGnN9yvxKj5UA0xZXe9AFkp9sz5x3QCR11lIP8bObGOvVr7z6K9DAkh/Ey9bdM
        fbdN5fupfgDr3bGHHcv7ka3xtQKBgQDs+wsUADtK3STvkiV/bI0O6lJNYKJOhcYn
        yzwMfAU18kGEBUcNfiUrkhFJEoRHurC8hWqMebUmHKKZ0R+8vIGv4BejcvEXdLYt
        cwsjs/vpbY0W4lgaZH/V9Qpg7GPdLEhxPj/MVdKY0rp9kB9Tpo4w8n6wlqdnj9uv
        HHPau37+/wKBgBzXQ/6ZV5G9SMqnYkuCyCI8/RMwh0n58u+K4LvStWvjtFq8/rsd
        jDwyaTMPhGWPY79reVJJsGOijz7nE8SuOPsIPJikJkR+je13/7sKrn4GioZKAqUT
        5UDeSpIs+8n0QL6o98J0TGpe+bCPSvR4BMvnS+n0b7Z7/x8kz3tPDUNhAoGBAJD/
        TZjwR1cYFjhrYHwly+0bXD4x6T1IRqUkidpNq9aFIqcHn6DW1SFinybpqHxG80p2
        C2pmMXtfO/IHbXbKlEMrRutgMbmbVLhcUq2Gu5TozdH5rdSAN2OPKcmB+dxi8vQv
        FVQOEuwky6x2GWTSXOAAD5o2o7kO4Wi0bQKhhCO7AoGAOiexYhpgXtzURWDzlQFv
        nr9HyYRhxpRg97o5InO5uIkcoSPBb7FS4k8tFscz6DBlz92lDpI1dIB6yvku2JfV
        F0MacyZE7wWaeGV5sH053SZr4I5ySpvbB8S8Q8ZOpt/Zfq3kujfj7y4qUt6VPu/M
        1rAIceW5pPBxbqdlJHx3W6E=
        -----END PRIVATE KEY-----
        """;

    private static final String RSA_VALID_CERTIFICATE = """
        -----BEGIN CERTIFICATE-----
        MIIDrTCCApWgAwIBAgIQFt3rAO4w+uwvtcPwINij2DANBgkqhkiG9w0BAQsFADBz
        MQswCQYDVQQGEwJVUzERMA8GA1UECgwIQWtsaXZpdHkxFDASBgNVBAsMC0RldmVs
        b3BtZW50MRMwEQYDVQQIDApDYWxpZm9ybmlhMRIwEAYDVQQDDAlUZXN0IENBIDEx
        EjAQBgNVBAcMCVBhbG8gQWx0bzAeFw0yMTA3MjIyMjU0NTdaFw0yMjA4MjIyMzU0
        NTdaMBgxFjAUBgNVBAMMDSouZXhhbXBsZS5jb20wggEiMA0GCSqGSIb3DQEBAQUA
        A4IBDwAwggEKAoIBAQDp62LciNw3yMp90jeeGO+kdGmqZUQqRFZlckggahlxZnLf
        vckSpdw18+FOvybX+T4RT5KsCwWgl6eMDnyDZYnfK0mIk5TW6gX0oNOB/uR4n9Da
        uN2+owlC1144nWauH0xi7clR0OU/MHPYn+36bSIYLzOyxJYvs0fWU5j+wkl62nUo
        3yiII+h+tPhYmWCBmSDhXCD0BIoHzCYPA5+c60uws9tSuzy2l8qVa41HDOdIru79
        GVoYP8jXJoGugoQ/uJBLJd1dCh2Egfi9TOJZafR2hWkdY6wzfaC68fWhS1GAnzjC
        hoVCa0P6nUNYkj7BH4MDoHNUkrurU6zNsG7gK1lLAgMBAAGjgZcwgZQwGAYDVR0R
        BBEwD4INKi5leGFtcGxlLmNvbTAJBgNVHRMEAjAAMB8GA1UdIwQYMBaAFNrwPBOM
        SNJjl2B+QtjfJCjK4aIAMB0GA1UdDgQWBBT2vzUtTK6vtN5UHyiCZOFTKvPXXDAO
        BgNVHQ8BAf8EBAMCBaAwHQYDVR0lBBYwFAYIKwYBBQUHAwEGCCsGAQUFBwMCMA0G
        CSqGSIb3DQEBCwUAA4IBAQAwHVTQgjVvxlhE80xi7mKLJrRwAC04CysWe+q3WFTN
        xt5PPmT5ZCanL1aFh62UuvzNTyhcJEo/kuYo4PNEEtywMsLEXre4cWBbcFUyS2sM
        z84ikMiG885ZH3ZfdWwH/N/DLL1Ro8+pIxCyxBzgAXIbuDcvhSN8E0SRTTgtOyeJ
        r+xxf6axm340hzyQHUNv3U7g7H2KYZtnbf98criMcbVYHaxpRyPkDTfmzYRkl2nn
        TgUnkZ3b5BI8XFq6vltAmxwcV/8IjKYd9eKazJg/I+mnMYzIpLYTD/UkMHyUMxrC
        sES8isPpB+QNvHC4gVowAAkxnk216r3Ft0od41jTkrQF
        -----END CERTIFICATE-----
        """;

    private static final String RSA_INVALID_CERTIFICATE = """
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

    private static final String ECDSA_PRIVATE_KEY = """
        -----BEGIN PRIVATE KEY-----
        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
        UhQx4S7sO1syI8kFxelGag==
        -----END PRIVATE KEY-----
        """;

    private static final String ECDSA_VALID_CERTIFICATE = """
        -----BEGIN CERTIFICATE-----
        MIIEZDCCAsygAwIBAgIIe+JXoqra/IUwDQYJKoZIhvcNAQEMBQAwXjERMA8GA1UE
        AxMIc2VydmVyY2ExFDASBgNVBAsTC0RldmVsb3BtZW50MREwDwYDVQQKEwhBa2xp
        dml0eTETMBEGA1UECBMKQ2FsaWZvcm5pYTELMAkGA1UEBhMCVVMwHhcNMjQwMjA1
        MjAxMzMzWhcNMjkwMTA5MjAxMzMzWjAYMRYwFAYDVQQDDA0qLmV4YW1wbGUuY29t
        MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAqE4vDalktxNrVgq8Idae
        238z4Co9Xu4Do5PW2+gkO6LG2aqEf8HYbxHICEcpMZIy78GGnkQGG6KuoIyBJM+h
        /FMDMWb1meHds967jgE0cJc8OGyE8IpcGNJ13qpKRw12rYTaEKhTKXFzqL3hARjc
        jToxR++0nKpnPK3qEGotqShZn31nNNJHDQmDPsua8ZrFfJLxqzi2GpSVJ00VYe7a
        XWtvflxRLmFgnIhwCtZPhU90KnL1Aeer2b9N+dCdAWja9yq56g1+fErAmrm+lJRd
        z/taxLOSYaxupzSFKPWZRUCEDa4X2bZrfOjAnfHxeI2VxLA9LGuDAwTZeUnh/ddW
        ajo290/OFh1ON1BwbxuD3L8YifOjAP/rynQxwSlyPhlljaF2aGuH6HwCATAmCTiC
        t+XXi/B8I+chAB8+vON1lvPesZlCXBph59LQLrJHwcHCx4aLwdZzLtTosmEyCdMY
        IY/en7vNLbPVbLWCu2gD0+XD6dFNk1P6bR9fkdChH1HXAgMBAAGjbDBqMB0GA1Ud
        DgQWBBS6TIXoFDZ+ik9ZAL/1txt20PvZ6TAOBgNVHQ8BAf8EBAMCBaAwGAYDVR0R
        BBEwD4INKi5leGFtcGxlLmNvbTAfBgNVHSMEGDAWgBSqJw9lbKd9+WdnCKLbDLnS
        FxyoqzANBgkqhkiG9w0BAQwFAAOCAYEAdrU2sI4NMJB4Jb+8k1NTQoq6qh+VkKvq
        hHzc1F13tvXmErl43OjQkfcfCPOSxDeZh5POBycJArXqnbKXl5t7w8YJnWTeuIFm
        XoJ8ZHjMdSuKiU4waPrYVaY5t6q9bOcyXDGjObIiUH1UTn734aKZkVN3+Cwz4Ib0
        fKLjjfpOBXkNpSDps7T/7g0iQTfxwlQUBthVoUkpN5cQV+/xruIErWLdaKcEYN5A
        4LAnFfUGMtX9Qfr/IbWMuuZuUTzTPjumYn3ccx70rgItCG30dm8RJSyqUx8f43yI
        VnuLCZ3YyV1J5vN56rdOUS+zrMnXJy404eD5aV4g46iyKESg7a8v+ABQ3QkgNnpb
        gUGyiltJyjNMVdFf8UC/CfPBySFB1Hfo7g3/y8FNB7UKLJS0UDm68/mjLUwmasUr
        nfjfcABz8ZwaNGqlb4qWJfA+yI/UCL1FpIBZiaEWmhZsCQwgsFk8vXpI52e3IUxt
        EzpLY+k37xLdk1xkBe59n+hL/hecSiXF
        -----END CERTIFICATE-----
        """;

    private static final char[] PASSWORD = "generated".toCharArray();

    @Test
    public void shouldVerifyRsaValid() throws Exception
    {
        // GIVEN
        TlsKeyPairVerifier verifier = new TlsKeyPairVerifier();
        KeyStore.PrivateKeyEntry entry = privateKeyEntry(RSA_PRIVATE_KEY, RSA_VALID_CERTIFICATE, PASSWORD);

        // WHEN
        boolean valid = verifier.verify(entry);

        // THEN
        assertThat(valid, equalTo(true));
    }

    @Test
    public void shouldVerifyRsaInvalid() throws Exception
    {
        // GIVEN
        TlsKeyPairVerifier verifier = new TlsKeyPairVerifier();
        KeyStore.PrivateKeyEntry entry = privateKeyEntry(RSA_PRIVATE_KEY, RSA_INVALID_CERTIFICATE, PASSWORD);

        // WHEN
        boolean valid = verifier.verify(entry);

        // THEN
        assertThat(valid, equalTo(false));
    }

    @Test
    public void shouldVerifyEcdsaValid() throws Exception
    {
        // GIVEN
        TlsKeyPairVerifier verifier = new TlsKeyPairVerifier();
        KeyStore.PrivateKeyEntry entry = privateKeyEntry(ECDSA_PRIVATE_KEY, ECDSA_VALID_CERTIFICATE, PASSWORD);

        // WHEN
        boolean valid = verifier.verify(entry);

        // THEN
        assertThat(valid, equalTo(true));
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
