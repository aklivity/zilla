/*
 * Copyright 2021-2026 Aklivity Inc.
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

import static io.aklivity.zilla.runtime.common.x509.X509Fields.SUBJECT_CN;
import static io.aklivity.zilla.runtime.common.x509.X509Fields.SUBJECT_DN;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThrows;

import java.util.Map;
import java.util.regex.MatchResult;

import org.junit.Test;

import io.aklivity.zilla.config.binding.tls.TlsWithConfig;
import io.aklivity.zilla.runtime.common.lang.util.function.LongObjectBiFunction;

public class TlsWithResolverTest
{
    private static final long SESSION_ID = 42L;

    private static final LongObjectBiFunction<MatchResult, String> IDENTITY =
        (authorization, match) -> authorization == SESSION_ID ? "client1" : "";
    private static final LongObjectBiFunction<MatchResult, String> ATTRIBUTE =
        (authorization, match) -> "certificate".equals(match.group(2)) ? "client2" : "";

    @Test
    public void shouldResolveLiteralSubjectCommonName()
    {
        TlsWithResolver resolver = newResolver(SUBJECT_CN, "client1");

        assertThat(resolver.resolve(SESSION_ID), equalTo(Map.of(SUBJECT_CN, "client1")));
    }

    @Test
    public void shouldResolveGuardedIdentity()
    {
        TlsWithResolver resolver = newResolver(SUBJECT_CN, "${guarded['x509_0'].identity}");

        assertThat(resolver.resolve(SESSION_ID), equalTo(Map.of(SUBJECT_CN, "client1")));
    }

    @Test
    public void shouldResolveGuardedAttribute()
    {
        TlsWithResolver resolver = newResolver(SUBJECT_CN, "${guarded['x509_0'].attributes.certificate}");

        assertThat(resolver.resolve(SESSION_ID), equalTo(Map.of(SUBJECT_CN, "client2")));
    }

    @Test
    public void shouldResolveEveryProperty()
    {
        TlsWithResolver resolver = new TlsWithResolver("test:app0", IDENTITY, ATTRIBUTE, TlsWithConfig.builder()
            .certificate()
                .subjectCommonName("${guarded['x509_0'].identity}")
                .subjectDistinguishedName("CN=Client1,O=Aklivity")
                .build()
            .build());

        assertThat(resolver.resolve(SESSION_ID),
            equalTo(Map.of(SUBJECT_CN, "client1", SUBJECT_DN, "cn=client1,o=aklivity")));
    }

    @Test
    public void shouldCanonicalizeLiteralSubjectDistinguishedName()
    {
        TlsWithResolver resolver = newResolver(SUBJECT_DN, "CN=Client1, O=Aklivity,  C=US");

        assertThat(resolver.resolve(SESSION_ID), equalTo(Map.of(SUBJECT_DN, "cn=client1,o=aklivity,c=us")));
    }

    @Test
    public void shouldRejectLiteralSubjectDistinguishedNameThatDoesNotParse()
    {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> newResolver(SUBJECT_DN, "not a distinguished name"));

        assertThat(ex.getMessage(), equalTo(
            "binding test:app0 route with.certificate.subject.dn is not a distinguished name: not a distinguished name"));
    }

    @Test
    public void shouldNotCanonicalizeExpressionSubjectDistinguishedName()
    {
        TlsWithResolver resolver = newResolver(SUBJECT_DN, "${guarded['x509_0'].identity}");

        assertThat(resolver.resolve(SESSION_ID), equalTo(Map.of(SUBJECT_DN, "client1")));
    }

    @Test
    public void shouldNotResolveUnresolvedExpression()
    {
        TlsWithResolver resolver = newResolver(SUBJECT_CN, "${guarded['x509_0'].identity}");

        assertThat(resolver.resolve(0L), nullValue());
        assertThat(resolver.unresolvedField(0L), equalTo(SUBJECT_CN));
    }

    @Test
    public void shouldNotResolveWhenOnePropertyIsUnresolved()
    {
        TlsWithResolver resolver = new TlsWithResolver("test:app0", IDENTITY, ATTRIBUTE, TlsWithConfig.builder()
            .certificate()
                .subjectCommonName("client1")
                .subjectDistinguishedName("${guarded['x509_0'].identity}")
                .build()
            .build());

        assertThat(resolver.resolve(0L), nullValue());
        assertThat(resolver.unresolvedField(0L), equalTo(SUBJECT_DN));
    }

    @Test
    public void shouldReportFieldNames()
    {
        TlsWithResolver resolver = new TlsWithResolver("test:app0", IDENTITY, ATTRIBUTE, TlsWithConfig.builder()
            .certificate()
                .subjectCommonName("client1")
                .subjectDistinguishedName("CN=client1")
                .build()
            .build());

        assertThat(resolver.fieldNames(), containsInAnyOrder(SUBJECT_CN, SUBJECT_DN));
    }

    @Test
    public void shouldExtractGuardNameFromIdentity()
    {
        TlsWithConfig with = TlsWithConfig.builder()
            .certificate()
                .subjectCommonName("${guarded['x509_0'].identity}")
                .build()
            .build();

        assertThat(TlsWithResolver.extractGuardNames(with), contains("x509_0"));
    }

    @Test
    public void shouldExtractGuardNameFromAttribute()
    {
        TlsWithConfig with = TlsWithConfig.builder()
            .certificate()
                .subjectCommonName("${guarded['x509_1'].attributes.certificate}")
                .build()
            .build();

        assertThat(TlsWithResolver.extractGuardNames(with), contains("x509_1"));
    }

    @Test
    public void shouldExtractNoGuardNamesFromLiterals()
    {
        TlsWithConfig with = TlsWithConfig.builder()
            .certificate()
                .subjectCommonName("client1")
                .build()
            .build();

        assertThat(TlsWithResolver.extractGuardNames(with), empty());
    }

    @Test
    public void shouldExtractNoGuardNamesWithoutCertificate()
    {
        assertThat(TlsWithResolver.extractGuardNames(TlsWithConfig.builder().build()), empty());
        assertThat(TlsWithResolver.extractGuardNames(null), empty());
    }

    private static TlsWithResolver newResolver(
        String name,
        String value)
    {
        return new TlsWithResolver("test:app0", IDENTITY, ATTRIBUTE, TlsWithConfig.builder()
            .certificate()
                .field(name, value)
                .build()
            .build());
    }
}
