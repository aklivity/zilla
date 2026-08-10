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
package io.aklivity.zilla.config.binding.tls.internal;

import static io.aklivity.zilla.config.binding.tls.TlsWithCertificateConfig.SUBJECT_CN;
import static io.aklivity.zilla.config.binding.tls.TlsWithCertificateConfig.SUBJECT_DN;
import static java.util.function.Function.identity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.tls.TlsWithConfig;

public class TlsWithConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new TlsWithConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadWithCertificateSubjectCommonName()
    {
        String text =
                "{" +
                    "\"certificate\":" +
                    "{" +
                        "\"subject.cn\": \"client1\"" +
                    "}" +
                "}";

        TlsWithConfig with = jsonb.fromJson(text, TlsWithConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(with.certificate, not(nullValue()));
        assertThat(with.certificate.fields.get(SUBJECT_CN), equalTo("client1"));
    }

    @Test
    public void shouldReadWithCertificateSubjectDistinguishedName()
    {
        String text =
                "{" +
                    "\"certificate\":" +
                    "{" +
                        "\"subject.dn\": \"CN=client1,O=Aklivity,C=US\"" +
                    "}" +
                "}";

        TlsWithConfig with = jsonb.fromJson(text, TlsWithConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(with.certificate.fields.get(SUBJECT_DN), equalTo("CN=client1,O=Aklivity,C=US"));
    }

    @Test
    public void shouldReadWithCertificateGuardedIdentity()
    {
        String text =
                "{" +
                    "\"certificate\":" +
                    "{" +
                        "\"subject.cn\": \"${guarded['x509_0'].identity}\"" +
                    "}" +
                "}";

        TlsWithConfig with = jsonb.fromJson(text, TlsWithConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(with.certificate.fields.get(SUBJECT_CN), equalTo("${guarded['x509_0'].identity}"));
    }

    @Test
    public void shouldWriteWithCertificateSubjectCommonName()
    {
        TlsWithConfig with = TlsWithConfig.builder()
            .inject(identity())
            .certificate()
                .subjectCommonName("client1")
                .build()
            .build();

        String text = jsonb.toJson(with);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"certificate\":{\"subject.cn\":\"client1\"}}"));
    }

    @Test
    public void shouldWriteWithoutCertificate()
    {
        TlsWithConfig with = TlsWithConfig.builder().build();

        String text = jsonb.toJson(with);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{}"));
    }
}
