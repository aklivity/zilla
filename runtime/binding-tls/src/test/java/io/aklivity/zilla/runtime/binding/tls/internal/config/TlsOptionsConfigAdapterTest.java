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

import static io.aklivity.zilla.runtime.binding.tls.config.TlsMutualConfig.REQUESTED;
import static java.util.Arrays.asList;
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

import io.aklivity.zilla.runtime.binding.tls.config.TlsOptionsConfig;

public class TlsOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new TlsOptionsConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadOptions()
    {
        String text =
                "{" +
                    "\"version\": \"TLSv1.2\"" +
                "}";

        TlsOptionsConfig options = jsonb.fromJson(text, TlsOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.version, equalTo("TLSv1.2"));
    }

    @Test
    public void shouldWriteOptions()
    {
        TlsOptionsConfig options = TlsOptionsConfig.builder()
            .inject(identity())
            .version("TLSv1.2")
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"version\":\"TLSv1.2\",\"trustcacerts\":true}"));
    }

    @Test
    public void shouldReadOptionsWithKeys()
    {
        String text =
                "{" +
                    "\"trust\": [ \"serverca\" ]" +
                "}";

        TlsOptionsConfig options = jsonb.fromJson(text, TlsOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.trust, equalTo(asList("serverca")));
    }

    @Test
    public void shouldWriteOptionsWithKeys()
    {
        TlsOptionsConfig options = TlsOptionsConfig.builder()
                .inject(identity())
                .keys(asList("localhost"))
                .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"keys\":[\"localhost\"],\"trustcacerts\":true}"));
    }

    @Test
    public void shouldReadOptionsWithTrust()
    {
        String text =
                "{" +
                    "\"trust\": [ \"serverca\" ]" +
                "}";

        TlsOptionsConfig options = jsonb.fromJson(text, TlsOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.trust, equalTo(asList("serverca")));
    }

    @Test
    public void shouldWriteOptionsWithTrust()
    {
        TlsOptionsConfig options = TlsOptionsConfig.builder()
            .inject(identity())
            .trust(asList("serverca"))
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"trust\":[\"serverca\"]}"));
    }

    @Test
    public void shouldReadOptionsWithTrustcacerts()
    {
        String text =
                "{" +
                    "\"trustcacerts\": true" +
                "}";

        TlsOptionsConfig options = jsonb.fromJson(text, TlsOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.trustcacerts, equalTo(true));
    }

    @Test
    public void shouldWriteOptionsWithTrustcacerts()
    {
        TlsOptionsConfig options = TlsOptionsConfig.builder()
            .inject(identity())
            .trustcacerts(true)
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"trustcacerts\":true}"));
    }

    @Test
    public void shouldReadOptionsWithServerName()
    {
        String text =
                "{" +
                    "\"sni\": [ \"example.net\" ]" +
                "}";

        TlsOptionsConfig options = jsonb.fromJson(text, TlsOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.sni, equalTo(asList("example.net")));
    }

    @Test
    public void shouldWriteOptionsWithServerName()
    {
        TlsOptionsConfig options = TlsOptionsConfig.builder()
            .inject(identity())
            .sni(asList("example.net"))
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"trustcacerts\":true,\"sni\":[\"example.net\"]}"));
    }

    @Test
    public void shouldReadOptionsWithAlpn()
    {
        String text =
                "{" +
                    "\"alpn\": [ \"echo\" ]" +
                "}";

        TlsOptionsConfig options = jsonb.fromJson(text, TlsOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.alpn, equalTo(asList("echo")));
    }

    @Test
    public void shouldWriteOptionsWithAlpn()
    {
        TlsOptionsConfig options = TlsOptionsConfig.builder()
            .inject(identity())
            .alpn(asList("echo"))
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"trustcacerts\":true,\"alpn\":[\"echo\"]}"));
    }

    @Test
    public void shouldReadOptionsWithMutual()
    {
        String text =
                "{" +
                    "\"mutual\": \"requested\"" +
                "}";

        TlsOptionsConfig options = jsonb.fromJson(text, TlsOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.mutual, equalTo(REQUESTED));
    }

    @Test
    public void shouldWriteOptionsWithMutual()
    {
        TlsOptionsConfig options = TlsOptionsConfig.builder()
            .inject(identity())
            .mutual(REQUESTED)
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"trustcacerts\":true,\"mutual\":\"requested\"}"));
    }

    @Test
    public void shouldReadOptionsWithSigners()
    {
        String text =
                "{" +
                    "\"signers\": [ \"clientca\" ]" +
                "}";

        TlsOptionsConfig options = jsonb.fromJson(text, TlsOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.signers, equalTo(asList("clientca")));
    }

    @Test
    public void shouldWriteOptionsWithSigners()
    {
        TlsOptionsConfig options = TlsOptionsConfig.builder()
            .inject(identity())
            .signers(asList("clientca"))
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"trustcacerts\":true,\"signers\":[\"clientca\"]}"));
    }
}
