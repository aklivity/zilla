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
package io.aklivity.zilla.specs.guard.x509.certificates;

import static io.aklivity.zilla.specs.guard.x509.certificates.X509Certificates.PARTNER_CHAIN;
import static io.aklivity.zilla.specs.guard.x509.certificates.X509Certificates.PLATFORM_CHAIN;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.io.ByteArrayInputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;

import org.junit.Test;

public class X509CertificatesTest
{
    @Test
    public void shouldParsePlatformChain() throws Exception
    {
        List<X509Certificate> chain = parse(PLATFORM_CHAIN);

        assertThat(chain.size(), equalTo(2));
        assertThat(chain.get(0).getSubjectX500Principal().getName(), equalTo(
            "CN=platform.example.com,OU=Engineering,OU=Platform,O=Example Inc,C=US"));
        assertThat(chain.get(1).getSubjectX500Principal().getName(), equalTo(
            "CN=Internal CA,O=Example Inc,C=US"));
    }

    @Test
    public void shouldParsePartnerChain() throws Exception
    {
        List<X509Certificate> chain = parse(PARTNER_CHAIN);

        assertThat(chain.size(), equalTo(2));
        assertThat(chain.get(0).getSubjectX500Principal().getName(), equalTo(
            "CN=partner.example.net,OU=Integration,O=Partner Inc,C=US"));
        assertThat(chain.get(1).getSubjectX500Principal().getName(), equalTo(
            "CN=Partner Issuing CA,O=Partner Inc,C=US"));
    }

    private static List<X509Certificate> parse(
        String pem) throws Exception
    {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        Collection<? extends Certificate> certificates =
            factory.generateCertificates(new ByteArrayInputStream(pem.getBytes(US_ASCII)));

        return certificates.stream()
            .map(X509Certificate.class::cast)
            .collect(toList());
    }
}
