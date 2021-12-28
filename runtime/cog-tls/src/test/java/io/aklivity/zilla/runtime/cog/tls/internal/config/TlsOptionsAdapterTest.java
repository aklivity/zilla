/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.runtime.cog.tls.internal.config;

import static io.aklivity.zilla.runtime.cog.tls.internal.config.TlsMutual.REQUESTED;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

public class TlsOptionsAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new TlsOptionsAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadOptions()
    {
        String text =
                "{" +
                    "\"version\": \"TLSv1.2\"" +
                "}";

        TlsOptions options = jsonb.fromJson(text, TlsOptions.class);

        assertThat(options, not(nullValue()));
        assertThat(options.version, equalTo("TLSv1.2"));
    }

    @Test
    public void shouldWriteOptions()
    {
        TlsOptions options = new TlsOptions("TLSv1.2", null, null, null, null, null, null, false);

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"version\":\"TLSv1.2\"}"));
    }

    @Test
    public void shouldReadOptionsWithKeys()
    {
        String text =
                "{" +
                    "\"trust\": [ \"serverca\" ]" +
                "}";

        TlsOptions options = jsonb.fromJson(text, TlsOptions.class);

        assertThat(options, not(nullValue()));
        assertThat(options.trust, equalTo(asList("serverca")));
    }

    @Test
    public void shouldWriteOptionsWithKeys()
    {
        TlsOptions options = new TlsOptions(null, asList("localhost"), null, null, null, null, null, false);

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"keys\":[\"localhost\"]}"));
    }

    @Test
    public void shouldReadOptionsWithTrust()
    {
        String text =
                "{" +
                    "\"trust\": [ \"serverca\" ]" +
                "}";

        TlsOptions options = jsonb.fromJson(text, TlsOptions.class);

        assertThat(options, not(nullValue()));
        assertThat(options.trust, equalTo(asList("serverca")));
    }

    @Test
    public void shouldWriteOptionsWithTrust()
    {
        TlsOptions options = new TlsOptions(null, null, asList("serverca"), null, null, null, null, false);

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

        TlsOptions options = jsonb.fromJson(text, TlsOptions.class);

        assertThat(options, not(nullValue()));
        assertThat(options.trustcacerts, equalTo(true));
    }

    @Test
    public void shouldWriteOptionsWithTrustcacerts()
    {
        TlsOptions options = new TlsOptions(null, null, null, null, null, null, null, true);

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

        TlsOptions options = jsonb.fromJson(text, TlsOptions.class);

        assertThat(options, not(nullValue()));
        assertThat(options.sni, equalTo(asList("example.net")));
    }

    @Test
    public void shouldWriteOptionsWithServerName()
    {
        TlsOptions options = new TlsOptions(null, null, null, asList("example.net"), null, null, null, false);

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"sni\":[\"example.net\"]}"));
    }

    @Test
    public void shouldReadOptionsWithAlpn()
    {
        String text =
                "{" +
                    "\"alpn\": [ \"echo\" ]" +
                "}";

        TlsOptions options = jsonb.fromJson(text, TlsOptions.class);

        assertThat(options, not(nullValue()));
        assertThat(options.alpn, equalTo(asList("echo")));
    }

    @Test
    public void shouldWriteOptionsWithAlpn()
    {
        TlsOptions options = new TlsOptions(null, null, null, null, asList("echo"), null, null, false);

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"alpn\":[\"echo\"]}"));
    }

    @Test
    public void shouldReadOptionsWithMutual()
    {
        String text =
                "{" +
                    "\"mutual\": \"requested\"" +
                "}";

        TlsOptions options = jsonb.fromJson(text, TlsOptions.class);

        assertThat(options, not(nullValue()));
        assertThat(options.mutual, equalTo(REQUESTED));
    }

    @Test
    public void shouldWriteOptionsWithMutual()
    {
        TlsOptions options = new TlsOptions(null, null, null, null, null, REQUESTED, null, false);

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"mutual\":\"requested\"}"));
    }

    @Test
    public void shouldReadOptionsWithSigners()
    {
        String text =
                "{" +
                    "\"signers\": [ \"clientca\" ]" +
                "}";

        TlsOptions options = jsonb.fromJson(text, TlsOptions.class);

        assertThat(options, not(nullValue()));
        assertThat(options.signers, equalTo(asList("clientca")));
    }

    @Test
    public void shouldWriteOptionsWithSigners()
    {
        TlsOptions options =
                new TlsOptions(null, null, null, null, null, null, asList("clientca"), false);

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"signers\":[\"clientca\"]}"));
    }
}
