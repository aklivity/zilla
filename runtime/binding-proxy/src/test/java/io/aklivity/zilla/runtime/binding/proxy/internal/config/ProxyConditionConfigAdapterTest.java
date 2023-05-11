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
package io.aklivity.zilla.runtime.binding.proxy.internal.config;

import static org.agrona.BitUtil.fromHex;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

public class ProxyConditionConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new ProxyConditionConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadCondition()
    {
        String text = "{}";

        ProxyConditionConfig condition = jsonb.fromJson(text, ProxyConditionConfig.class);

        assertThat(condition, not(nullValue()));
    }

    @Test
    public void shouldWriteCondition()
    {
        ProxyConditionConfig condition = new ProxyConditionConfig(null, null, null, null, null);

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{}"));
    }

    @Test
    public void shouldReadConditionWithTransport()
    {
        String text =
                "{" +
                    "\"transport\": \"stream\"" +
                "}";

        ProxyConditionConfig condition = jsonb.fromJson(text, ProxyConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.transport, equalTo("stream"));
    }

    @Test
    public void shouldWriteConditionWithTransport()
    {
        ProxyConditionConfig condition = new ProxyConditionConfig("stream", null, null, null, null);

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"transport\":\"stream\"}"));
    }

    @Test
    public void shouldReadConditionWithFamily()
    {
        String text =
                "{" +
                    "\"family\": \"inet\"" +
                "}";

        ProxyConditionConfig condition = jsonb.fromJson(text, ProxyConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.family, equalTo("inet"));
    }

    @Test
    public void shouldWriteConditionWithFamily()
    {
        ProxyConditionConfig condition = new ProxyConditionConfig(null, "inet", null, null, null);

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"family\":\"inet\"}"));
    }

    @Test
    public void shouldReadConditionWithSource()
    {
        String text =
                "{" +
                    "\"source\":" +
                    "{" +
                        "\"host\": \"127.0.0.0/24\"," +
                        "\"port\": 443" +
                    "}" +
                "}";

        ProxyConditionConfig condition = jsonb.fromJson(text, ProxyConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.source, not(nullValue()));
        assertThat(condition.source.host, equalTo("127.0.0.0/24"));
        assertThat(condition.source.port, equalTo(443));
    }

    @Test
    public void shouldWriteConditionWithSource()
    {
        ProxyConditionConfig condition =
                new ProxyConditionConfig(null, null, new ProxyAddressConfig("127.0.0.0/24", 443), null, null);

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"source\":{\"host\":\"127.0.0.0/24\",\"port\":443}}"));
    }

    @Test
    public void shouldReadConditionWithDestination()
    {
        String text =
                "{" +
                    "\"destination\":" +
                    "{" +
                        "\"host\": \"127.0.0.0/24\"," +
                        "\"port\": 443" +
                    "}" +
                "}";

        ProxyConditionConfig condition = jsonb.fromJson(text, ProxyConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.destination, not(nullValue()));
        assertThat(condition.destination.host, equalTo("127.0.0.0/24"));
        assertThat(condition.destination.port, equalTo(443));
    }

    @Test
    public void shouldWriteConditionWithDestination()
    {
        ProxyConditionConfig condition =
                new ProxyConditionConfig(null, null, null, new ProxyAddressConfig("127.0.0.0/24", 443), null);

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"destination\":{\"host\":\"127.0.0.0/24\",\"port\":443}}"));
    }

    @Test
    public void shouldReadConditionWithAlpn()
    {
        String text =
                "{" +
                    "\"info\":" +
                    "{" +
                        "\"alpn\": \"echo\"" +
                    "}" +
                "}";

        ProxyConditionConfig condition = jsonb.fromJson(text, ProxyConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.info, not(nullValue()));
        assertThat(condition.info.alpn, equalTo("echo"));
    }

    @Test
    public void shouldWriteConditionWithAlpn()
    {
        ProxyInfoConfig info = new ProxyInfoConfig("echo", null, null, null, null);
        ProxyConditionConfig condition = new ProxyConditionConfig(null, null, null, null, info);

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"info\":{\"alpn\":\"echo\"}}"));
    }

    @Test
    public void shouldReadConditionWithAuthority()
    {
        String text =
                "{" +
                    "\"info\":" +
                    "{" +
                        "\"authority\": \"example.com\"" +
                    "}" +
                "}";

        ProxyConditionConfig condition = jsonb.fromJson(text, ProxyConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.info, not(nullValue()));
        assertThat(condition.info.authority, equalTo("example.com"));
    }

    @Test
    public void shouldWriteConditionWithAuthority()
    {
        ProxyInfoConfig info = new ProxyInfoConfig(null, "example.com", null, null, null);
        ProxyConditionConfig condition = new ProxyConditionConfig(null, null, null, null, info);

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"info\":{\"authority\":\"example.com\"}}"));
    }

    @Test
    public void shouldReadConditionWithIdentity()
    {
        String text =
                "{" +
                    "\"info\":" +
                    "{" +
                        "\"identity\": \"12345678\"" +
                    "}" +
                "}";

        ProxyConditionConfig condition = jsonb.fromJson(text, ProxyConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.info, not(nullValue()));
        assertThat(condition.info.identity, equalTo(fromHex("12345678")));
    }

    @Test
    public void shouldWriteConditionWithIdentity()
    {
        ProxyInfoConfig info = new ProxyInfoConfig(null, null, fromHex("12345678"), null, null);
        ProxyConditionConfig condition = new ProxyConditionConfig(null, null, null, null, info);

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"info\":{\"identity\":\"12345678\"}}"));
    }

    @Test
    public void shouldReadConditionWithNamepsace()
    {
        String text =
                "{" +
                    "\"info\":" +
                    "{" +
                        "\"namespace\": \"example\"" +
                    "}" +
                "}";

        ProxyConditionConfig condition = jsonb.fromJson(text, ProxyConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.info, not(nullValue()));
        assertThat(condition.info.namespace, equalTo("example"));
    }

    @Test
    public void shouldWriteConditionWithNamespace()
    {
        ProxyInfoConfig info = new ProxyInfoConfig(null, null, null, "example", null);
        ProxyConditionConfig condition = new ProxyConditionConfig(null, null, null, null, info);

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"info\":{\"namespace\":\"example\"}}"));
    }

    @Test
    public void shouldReadConditionWithSecureVersion()
    {
        String text =
                "{" +
                    "\"info\":" +
                    "{" +
                        "\"secure\":" +
                        "{" +
                            "\"version\": \"TLSv1.3\"" +
                        "}" +
                    "}" +
                "}";

        ProxyConditionConfig condition = jsonb.fromJson(text, ProxyConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.info, not(nullValue()));
        assertThat(condition.info.secure, not(nullValue()));
        assertThat(condition.info.secure.version, equalTo("TLSv1.3"));
    }

    @Test
    public void shouldWriteConditionWithSecureVersion()
    {
        ProxySecureInfoConfig secureInfo = new ProxySecureInfoConfig("TLSv1.3", null, null, null, null);
        ProxyInfoConfig info = new ProxyInfoConfig(null, null, null, null, secureInfo);
        ProxyConditionConfig condition = new ProxyConditionConfig(null, null, null, null, info);

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"info\":{\"secure\":{\"version\":\"TLSv1.3\"}}}"));
    }

    @Test
    public void shouldReadConditionWithSecureCipher()
    {
        String text =
                "{" +
                    "\"info\":" +
                    "{" +
                        "\"secure\":" +
                        "{" +
                            "\"cipher\": \"ECDHE-RSA-AES128-GCM-SHA256\"" +
                        "}" +
                    "}" +
                "}";

        ProxyConditionConfig condition = jsonb.fromJson(text, ProxyConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.info, not(nullValue()));
        assertThat(condition.info.secure, not(nullValue()));
        assertThat(condition.info.secure.cipher, equalTo("ECDHE-RSA-AES128-GCM-SHA256"));
    }

    @Test
    public void shouldWriteConditionWithSecureCipher()
    {
        ProxySecureInfoConfig secureInfo = new ProxySecureInfoConfig(null, "ECDHE-RSA-AES128-GCM-SHA256", null, null, null);
        ProxyInfoConfig info = new ProxyInfoConfig(null, null, null, null, secureInfo);
        ProxyConditionConfig condition = new ProxyConditionConfig(null, null, null, null, info);

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"info\":{\"secure\":{\"cipher\":\"ECDHE-RSA-AES128-GCM-SHA256\"}}}"));
    }

    @Test
    public void shouldReadConditionWithSecureKey()
    {
        String text =
                "{" +
                    "\"info\":" +
                    "{" +
                        "\"secure\":" +
                        "{" +
                            "\"key\": \"RSA2048\"" +
                        "}" +
                    "}" +
                "}";

        ProxyConditionConfig condition = jsonb.fromJson(text, ProxyConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.info, not(nullValue()));
        assertThat(condition.info.secure, not(nullValue()));
        assertThat(condition.info.secure.key, equalTo("RSA2048"));
    }

    @Test
    public void shouldWriteConditionWithSecureKey()
    {
        ProxySecureInfoConfig secureInfo = new ProxySecureInfoConfig(null, null, "RSA2048", null, null);
        ProxyInfoConfig info = new ProxyInfoConfig(null, null, null, null, secureInfo);
        ProxyConditionConfig condition = new ProxyConditionConfig(null, null, null, null, info);

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"info\":{\"secure\":{\"key\":\"RSA2048\"}}}"));
    }

    @Test
    public void shouldReadConditionWithSecureName()
    {
        String text =
                "{" +
                    "\"info\":" +
                    "{" +
                        "\"secure\":" +
                        "{" +
                            "\"name\": \"name@domain\"" +
                        "}" +
                    "}" +
                "}";

        ProxyConditionConfig condition = jsonb.fromJson(text, ProxyConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.info, not(nullValue()));
        assertThat(condition.info.secure, not(nullValue()));
        assertThat(condition.info.secure.name, equalTo("name@domain"));
    }

    @Test
    public void shouldWriteConditionWithSecureName()
    {
        ProxySecureInfoConfig secureInfo = new ProxySecureInfoConfig(null, null, null, "name@domain", null);
        ProxyInfoConfig info = new ProxyInfoConfig(null, null, null, null, secureInfo);
        ProxyConditionConfig condition = new ProxyConditionConfig(null, null, null, null, info);

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"info\":{\"secure\":{\"name\":\"name@domain\"}}}"));
    }

    @Test
    public void shouldReadConditionWithSecureSignature()
    {
        String text =
                "{" +
                    "\"info\":" +
                    "{" +
                        "\"secure\":" +
                        "{" +
                            "\"signature\": \"SHA256\"" +
                        "}" +
                    "}" +
                "}";

        ProxyConditionConfig condition = jsonb.fromJson(text, ProxyConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.info, not(nullValue()));
        assertThat(condition.info.secure, not(nullValue()));
        assertThat(condition.info.secure.signature, equalTo("SHA256"));
    }

    @Test
    public void shouldWriteConditionWithSecureSignature()
    {
        ProxySecureInfoConfig secureInfo = new ProxySecureInfoConfig(null, null, null, null, "SHA256");
        ProxyInfoConfig info = new ProxyInfoConfig(null, null, null, null, secureInfo);
        ProxyConditionConfig condition = new ProxyConditionConfig(null, null, null, null, info);

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"info\":{\"secure\":{\"signature\":\"SHA256\"}}}"));
    }
}
