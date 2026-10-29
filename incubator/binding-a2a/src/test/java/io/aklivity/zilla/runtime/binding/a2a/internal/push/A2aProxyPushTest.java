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

package io.aklivity.zilla.runtime.binding.a2a.internal.push;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

import java.io.StringReader;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

import org.junit.Test;

import io.aklivity.zilla.config.engine.GenericVaultConfig;
import io.aklivity.zilla.config.engine.VaultConfig;
import io.aklivity.zilla.config.engine.test.internal.vault.config.TestVaultOptionsConfig;
import io.aklivity.zilla.runtime.engine.test.internal.vault.TestVaultHandler;

public class A2aProxyPushTest
{
    private static final String KEY_PEM =
        "-----BEGIN SECRET KEY-----\n" +
        "jOl3tg1ZDuqTrzB7WOVaUOEoezst8sz/ywPPbsGE8bA=\n" +
        "-----END SECRET KEY-----";

    private static final String BASE = "http://localhost:8080/a2a/push";

    private static final String CREATE =
        """
        {"taskId":"t-1","configId":"c-1","config":{"url":"https://client.example.com/webhook","token":"tok-1",
        "authentication":{"scheme":"Bearer","credentials":"cred-1"}}}""".replace("\n", "");

    private final A2aPushSealer sealer = sealer();
    private final Clock clock = Clock.fixed(Instant.ofEpochSecond(1000L), ZoneOffset.UTC);

    @Test
    public void shouldSealCreateAndUnsealResponse()
    {
        final A2aProxyPush push = push(2048, null);

        final JsonObject sealed = push.sealCreate("weather", "t-1", json(CREATE));

        assertThat(sealed, notNullValue());
        final JsonObject config = sealed.getJsonObject("config");
        assertThat(config.getString("url"), startsWith(BASE + "/"));
        assertThat(config.containsKey("authentication"), equalTo(false));
        assertThat(config.getString("token"), not(equalTo("tok-1")));
        assertThat(sealed.getString("configId"), equalTo("c-1"));

        final JsonObject response = Json.createObjectBuilder()
            .add("id", "c-1")
            .add("taskId", "t-1")
            .add("url", config.getString("url"))
            .add("token", config.getString("token"))
            .build();

        final JsonValue unsealed = push.unsealResponse(response);

        assertThat(unsealed, equalTo(json(
            "{\"id\":\"c-1\",\"taskId\":\"t-1\",\"url\":\"https://client.example.com/webhook\",\"token\":\"tok-1\"," +
            "\"authentication\":{\"scheme\":\"Bearer\",\"credentials\":\"cred-1\"}}")));
    }

    @Test
    public void shouldAddNonceTokenWhenConfigHasNone()
    {
        final JsonObject sealed = push(2048, null).sealCreate("weather", "t-1",
            json("{\"taskId\":\"t-1\",\"config\":{\"url\":\"https://client.example.com/webhook\"}}"));

        assertThat(sealed.getJsonObject("config").containsKey("token"), equalTo(true));
    }

    @Test
    public void shouldUnsealListResponse()
    {
        final A2aProxyPush push = push(2048, null);
        final JsonObject config = push.sealCreate("weather", "t-1", json(CREATE)).getJsonObject("config");
        final JsonObject list = Json.createObjectBuilder()
            .add("configs", Json.createArrayBuilder().add(Json.createObjectBuilder()
                .add("id", "c-1").add("url", config.getString("url")).add("token", config.getString("token"))))
            .build();

        final JsonObject unsealed = push.unsealResponse(list).asJsonObject();

        assertThat(unsealed.getJsonArray("configs").getJsonObject(0).getString("url"),
            equalTo("https://client.example.com/webhook"));
    }

    @Test
    public void shouldNotUnsealForeignOrNonConfigResponses()
    {
        final A2aProxyPush push = push(2048, null);
        final JsonObject foreign = json("{\"id\":\"c-1\",\"url\":\"https://other.example.com/hook\",\"token\":\"x\"}");
        final JsonObject garbled = json("{\"id\":\"c-1\",\"url\":\"" + BASE + "/garbled\",\"token\":\"x\"}");

        assertThat(push.unsealResponse(foreign), equalTo(foreign));
        assertThat(push.unsealResponse(garbled), equalTo(garbled));
        assertThat(push.unsealResponse(JsonValue.NULL), equalTo(JsonValue.NULL));
    }

    @Test
    public void shouldNotSealCreateOverMaxLength()
    {
        assertThat(push(128, null).sealCreate("weather", "t-1", json(CREATE)), nullValue());
    }

    @Test
    public void shouldNotSealCreateForBlockedOrMissingTarget()
    {
        final A2aProxyPush push = push(2048, null);

        assertThat(push.sealCreate("weather", "t-1", json(
            "{\"taskId\":\"t-1\",\"config\":{\"url\":\"http://169.254.169.254/latest\"}}")), nullValue());
        assertThat(push.sealCreate("weather", "t-1", json("{\"taskId\":\"t-1\",\"config\":{}}")), nullValue());
        assertThat(push.sealCreate("weather", "t-1", json("{\"taskId\":\"t-1\"}")), nullValue());
    }

    @Test
    public void shouldNotSealCreateOutsideAllowList()
    {
        assertThat(push(2048, new A2aPushPolicy(List.of("https://*.example.org/*")))
            .sealCreate("weather", "t-1", json(CREATE)), nullValue());
    }

    @Test
    public void shouldSealInlineConfigForBothMemberNames()
    {
        final A2aProxyPush push = push(2048, null);

        for (String name : List.of("taskPushNotificationConfig", "pushNotificationConfig"))
        {
            final JsonObject sealed = push.sealSend("weather", json(
                "{\"message\":{\"messageId\":\"m-1\",\"taskId\":\"t-1\"},\"configuration\":{\"blocking\":true,\"" + name +
                "\":{\"url\":\"https://client.example.com/webhook\",\"token\":\"tok-1\"}}}"));

            final JsonObject configuration = sealed.getJsonObject("configuration");
            assertThat(configuration.getBoolean("blocking"), equalTo(true));
            assertThat(configuration.getJsonObject(name).getString("url"), startsWith(BASE + "/"));
            assertThat(sealed.getJsonObject("message").getString("messageId"), equalTo("m-1"));
        }
    }

    @Test
    public void shouldPassSendWithoutInlineConfig()
    {
        final A2aProxyPush push = push(2048, null);
        final JsonObject plain = json("{\"message\":{\"messageId\":\"m-1\"}}");
        final JsonObject configured = json("{\"message\":{\"messageId\":\"m-1\"},\"configuration\":{\"blocking\":true}}");

        assertThat(push.sealSend("weather", plain), equalTo(plain));
        assertThat(push.sealSend("weather", configured), equalTo(configured));
    }

    @Test
    public void shouldRejectSendWithBlockedInlineConfig()
    {
        assertThat(push(2048, null).sealSend("weather", json(
            "{\"message\":{\"messageId\":\"m-1\"},\"configuration\":{\"taskPushNotificationConfig\":" +
            "{\"url\":\"http://127.0.0.1/hook\"}}}")), nullValue());
    }

    @Test
    public void shouldUnwrapNotification()
    {
        final A2aProxyPush push = push(2048, null);
        final JsonObject config = push.sealCreate("weather", "t-1", json(CREATE)).getJsonObject("config");
        final String token = config.getString("url").substring(BASE.length() + 1);

        final A2aPushSeal seal = push.unwrap(token, config.getString("token"));

        assertThat(seal.agent(), equalTo("weather"));
        assertThat(seal.taskId(), equalTo("t-1"));
        assertThat(seal.url(), equalTo("https://client.example.com/webhook"));
        assertThat(seal.token(), equalTo("tok-1"));
        assertThat(seal.authentication().getString("credentials"), equalTo("cred-1"));
    }

    @Test
    public void shouldNotUnwrapNotificationWithWrongOrMissingNonce()
    {
        final A2aProxyPush push = push(2048, null);
        final JsonObject config = push.sealCreate("weather", "t-1", json(CREATE)).getJsonObject("config");
        final String token = config.getString("url").substring(BASE.length() + 1);

        assertThat(push.unwrap(token, "wrong"), nullValue());
        assertThat(push.unwrap(token, null), nullValue());
        assertThat(push.unwrap(null, config.getString("token")), nullValue());
        assertThat(push.unwrap("garbled", config.getString("token")), nullValue());
    }

    @Test
    public void shouldNotUnwrapExpiredNotification()
    {
        final A2aProxyPush sealing = push(2048, null);
        final JsonObject config = sealing.sealCreate("weather", "t-1", json(CREATE)).getJsonObject("config");
        final String token = config.getString("url").substring(BASE.length() + 1);
        final A2aProxyPush later = new A2aProxyPush(BASE, sealer, new A2aPushPolicy(null), 3600L, 2048,
            Clock.fixed(Instant.ofEpochSecond(1000L + 3601L), ZoneOffset.UTC), 0L, null);

        assertThat(sealing.unwrap(token, config.getString("token")), notNullValue());
        assertThat(later.unwrap(token, config.getString("token")), nullValue());
    }

    @Test
    public void shouldNotUnwrapNotificationWhenTargetNoLongerPermitted()
    {
        final A2aProxyPush sealing = push(2048, null);
        final JsonObject config = sealing.sealCreate("weather", "t-1", json(CREATE)).getJsonObject("config");
        final String token = config.getString("url").substring(BASE.length() + 1);
        final A2aProxyPush narrowed = push(2048, new A2aPushPolicy(List.of("https://*.example.org/*")));

        assertThat(narrowed.unwrap(token, config.getString("token")), nullValue());
    }

    @Test
    public void shouldWriteCompactBytes()
    {
        assertThat(new String(A2aProxyPush.bytes(json("{\"a\":1,\"b\":[true]}"))), equalTo("{\"a\":1,\"b\":[true]}"));
    }

    private A2aProxyPush push(
        int maxLength,
        A2aPushPolicy policy)
    {
        return new A2aProxyPush(BASE, sealer, policy != null ? policy : new A2aPushPolicy(null), 3600L, maxLength, clock,
            0L, null);
    }

    private static JsonObject json(
        String text)
    {
        return Json.createReader(new StringReader(text)).readObject();
    }

    private static A2aPushSealer sealer()
    {
        final VaultConfig vault = GenericVaultConfig.builder()
            .namespace("test")
            .name("secrets0")
            .type("test")
            .options(TestVaultOptionsConfig.builder().wrap("a2a-push", KEY_PEM).build())
            .build();

        return new A2aPushSealer(new TestVaultHandler(vault).initSecretKeys(List.of("a2a-push")).getSecretKeyManager(),
            "a2a-push");
    }
}
