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
package io.aklivity.zilla.runtime.cog.proxy.internal.config;

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

public class ProxyConditionAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new ProxyConditionAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadCondition()
    {
        String text = "{}";

        ProxyCondition condition = jsonb.fromJson(text, ProxyCondition.class);

        assertThat(condition, not(nullValue()));
    }

    @Test
    public void shouldWriteCondition()
    {
        ProxyCondition condition = new ProxyCondition(null, null, null, null, null);

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

        ProxyCondition condition = jsonb.fromJson(text, ProxyCondition.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.transport, equalTo("stream"));
    }

    @Test
    public void shouldWriteConditionWithTransport()
    {
        ProxyCondition condition = new ProxyCondition("stream", null, null, null, null);

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

        ProxyCondition condition = jsonb.fromJson(text, ProxyCondition.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.family, equalTo("inet"));
    }

    @Test
    public void shouldWriteConditionWithFamily()
    {
        ProxyCondition condition = new ProxyCondition(null, "inet", null, null, null);

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

        ProxyCondition condition = jsonb.fromJson(text, ProxyCondition.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.source, not(nullValue()));
        assertThat(condition.source.host, equalTo("127.0.0.0/24"));
        assertThat(condition.source.port, equalTo(443));
    }

    @Test
    public void shouldWriteConditionWithSource()
    {
        ProxyCondition condition = new ProxyCondition(null, null, new ProxyAddress("127.0.0.0/24", 443), null, null);

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

        ProxyCondition condition = jsonb.fromJson(text, ProxyCondition.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.destination, not(nullValue()));
        assertThat(condition.destination.host, equalTo("127.0.0.0/24"));
        assertThat(condition.destination.port, equalTo(443));
    }

    @Test
    public void shouldWriteConditionWithDestination()
    {
        ProxyCondition condition = new ProxyCondition(null, null, null, new ProxyAddress("127.0.0.0/24", 443), null);

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

        ProxyCondition condition = jsonb.fromJson(text, ProxyCondition.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.info, not(nullValue()));
        assertThat(condition.info.alpn, equalTo("echo"));
    }

    @Test
    public void shouldWriteConditionWithAlpn()
    {
        ProxyInfo info = new ProxyInfo("echo", null, null, null, null);
        ProxyCondition condition = new ProxyCondition(null, null, null, null, info);

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

        ProxyCondition condition = jsonb.fromJson(text, ProxyCondition.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.info, not(nullValue()));
        assertThat(condition.info.authority, equalTo("example.com"));
    }

    @Test
    public void shouldWriteConditionWithAuthority()
    {
        ProxyInfo info = new ProxyInfo(null, "example.com", null, null, null);
        ProxyCondition condition = new ProxyCondition(null, null, null, null, info);

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

        ProxyCondition condition = jsonb.fromJson(text, ProxyCondition.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.info, not(nullValue()));
        assertThat(condition.info.identity, equalTo(fromHex("12345678")));
    }

    @Test
    public void shouldWriteConditionWithIdentity()
    {
        ProxyInfo info = new ProxyInfo(null, null, fromHex("12345678"), null, null);
        ProxyCondition condition = new ProxyCondition(null, null, null, null, info);

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

        ProxyCondition condition = jsonb.fromJson(text, ProxyCondition.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.info, not(nullValue()));
        assertThat(condition.info.namespace, equalTo("example"));
    }

    @Test
    public void shouldWriteConditionWithNamespace()
    {
        ProxyInfo info = new ProxyInfo(null, null, null, "example", null);
        ProxyCondition condition = new ProxyCondition(null, null, null, null, info);

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

        ProxyCondition condition = jsonb.fromJson(text, ProxyCondition.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.info, not(nullValue()));
        assertThat(condition.info.secure, not(nullValue()));
        assertThat(condition.info.secure.version, equalTo("TLSv1.3"));
    }

    @Test
    public void shouldWriteConditionWithSecureVersion()
    {
        ProxySecureInfo secureInfo = new ProxySecureInfo("TLSv1.3", null, null, null, null);
        ProxyInfo info = new ProxyInfo(null, null, null, null, secureInfo);
        ProxyCondition condition = new ProxyCondition(null, null, null, null, info);

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

        ProxyCondition condition = jsonb.fromJson(text, ProxyCondition.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.info, not(nullValue()));
        assertThat(condition.info.secure, not(nullValue()));
        assertThat(condition.info.secure.cipher, equalTo("ECDHE-RSA-AES128-GCM-SHA256"));
    }

    @Test
    public void shouldWriteConditionWithSecureCipher()
    {
        ProxySecureInfo secureInfo = new ProxySecureInfo(null, "ECDHE-RSA-AES128-GCM-SHA256", null, null, null);
        ProxyInfo info = new ProxyInfo(null, null, null, null, secureInfo);
        ProxyCondition condition = new ProxyCondition(null, null, null, null, info);

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

        ProxyCondition condition = jsonb.fromJson(text, ProxyCondition.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.info, not(nullValue()));
        assertThat(condition.info.secure, not(nullValue()));
        assertThat(condition.info.secure.key, equalTo("RSA2048"));
    }

    @Test
    public void shouldWriteConditionWithSecureKey()
    {
        ProxySecureInfo secureInfo = new ProxySecureInfo(null, null, "RSA2048", null, null);
        ProxyInfo info = new ProxyInfo(null, null, null, null, secureInfo);
        ProxyCondition condition = new ProxyCondition(null, null, null, null, info);

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

        ProxyCondition condition = jsonb.fromJson(text, ProxyCondition.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.info, not(nullValue()));
        assertThat(condition.info.secure, not(nullValue()));
        assertThat(condition.info.secure.name, equalTo("name@domain"));
    }

    @Test
    public void shouldWriteConditionWithSecureName()
    {
        ProxySecureInfo secureInfo = new ProxySecureInfo(null, null, null, "name@domain", null);
        ProxyInfo info = new ProxyInfo(null, null, null, null, secureInfo);
        ProxyCondition condition = new ProxyCondition(null, null, null, null, info);

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

        ProxyCondition condition = jsonb.fromJson(text, ProxyCondition.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.info, not(nullValue()));
        assertThat(condition.info.secure, not(nullValue()));
        assertThat(condition.info.secure.signature, equalTo("SHA256"));
    }

    @Test
    public void shouldWriteConditionWithSecureSignature()
    {
        ProxySecureInfo secureInfo = new ProxySecureInfo(null, null, null, null, "SHA256");
        ProxyInfo info = new ProxyInfo(null, null, null, null, secureInfo);
        ProxyCondition condition = new ProxyCondition(null, null, null, null, info);

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"info\":{\"secure\":{\"signature\":\"SHA256\"}}}"));
    }
}
