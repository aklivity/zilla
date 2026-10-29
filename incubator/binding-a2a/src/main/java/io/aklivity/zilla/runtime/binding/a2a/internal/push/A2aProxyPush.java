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

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import io.aklivity.zilla.config.binding.a2a.A2aProxyPushConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;
import io.aklivity.zilla.runtime.engine.vault.SecretKeyManagerFactory;
import io.aklivity.zilla.runtime.engine.vault.VaultHandler;

public final class A2aProxyPush
{
    private static final String VERSION = "1.0";
    private static final int NONCE_LENGTH = 16;
    private static final List<String> INLINE_CONFIG_NAMES = List.of("taskPushNotificationConfig", "pushNotificationConfig");

    public final long exitId;
    public final GuardHandler guard;

    private final String url;
    private final A2aPushSealer sealer;
    private final A2aPushPolicy policy;
    private final long ttlSeconds;
    private final int maxLength;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public static A2aProxyPush of(
        BindingConfig binding,
        A2aProxyPushConfig push,
        EngineContext context)
    {
        A2aProxyPush result = null;

        if (push != null)
        {
            final VaultHandler vault = context.supplyVault(binding.resolveId.applyAsLong(push.vault));
            final SecretKeyManagerFactory factory = vault != null ? vault.initSecretKeys(List.of(push.key)) : null;

            if (push.url == null || factory == null)
            {
                throw new IllegalStateException(String.format("%s: push requires url and secret key %s in vault %s",
                    binding.name, push.key, push.vault));
            }

            final A2aPushSealer sealer = new A2aPushSealer(factory.getSecretKeyManager(), push.key);
            final GuardHandler guard = Optional.ofNullable(push.authorization)
                .map(a -> a.name)
                .map(binding.resolveId::applyAsLong)
                .map(context::supplyGuard)
                .orElse(null);
            final A2aProxyPush candidate = new A2aProxyPush(push.url, sealer, new A2aPushPolicy(push.allow),
                push.ttl.toSeconds(), push.maxLength, context.clock(), binding.resolveId.applyAsLong(push.exit), guard);

            if (candidate.seal(null, null, push.url, null, null) == null)
            {
                throw new IllegalStateException(String.format("%s: push cannot wrap with secret key %s in vault %s",
                    binding.name, push.key, push.vault));
            }

            result = candidate;
        }

        return result;
    }

    A2aProxyPush(
        String url,
        A2aPushSealer sealer,
        A2aPushPolicy policy,
        long ttlSeconds,
        int maxLength,
        Clock clock,
        long exitId,
        GuardHandler guard)
    {
        this.url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.sealer = sealer;
        this.policy = policy;
        this.ttlSeconds = ttlSeconds;
        this.maxLength = maxLength;
        this.clock = clock;
        this.exitId = exitId;
        this.guard = guard;
    }

    public JsonObject sealCreate(
        String agent,
        String taskId,
        JsonObject body)
    {
        JsonObject sealed = null;

        if (body.get("config") instanceof JsonObject config)
        {
            final JsonObject sealedConfig = sealConfig(agent, taskId, config);

            if (sealedConfig != null)
            {
                sealed = replace(body, "config", sealedConfig);
            }
        }

        return sealed;
    }

    public JsonObject sealSend(
        String agent,
        JsonObject body)
    {
        JsonObject sealed = body;

        if (body.get("configuration") instanceof JsonObject configuration)
        {
            final String inline = INLINE_CONFIG_NAMES.stream()
                .filter(name -> configuration.get(name) instanceof JsonObject)
                .findFirst()
                .orElse(null);

            if (inline != null)
            {
                final String taskId = body.get("message") instanceof JsonObject message ? string(message, "taskId") : null;
                final JsonObject sealedConfig = sealConfig(agent, taskId, configuration.getJsonObject(inline));

                sealed = sealedConfig != null
                    ? replace(body, "configuration", replace(configuration, inline, sealedConfig))
                    : null;
            }
        }

        return sealed;
    }

    public JsonValue unsealResponse(
        JsonValue response)
    {
        JsonValue unsealed = response;

        if (response instanceof JsonObject object)
        {
            if (object.get("configs") instanceof JsonArray configs)
            {
                final JsonArrayBuilder items = Json.createArrayBuilder();
                configs.forEach(item -> items.add(item instanceof JsonObject config ? unsealConfig(config) : item));

                final JsonObjectBuilder builder = Json.createObjectBuilder();
                object.forEach((name, value) -> builder.add(name, "configs".equals(name) ? items.build() : value));
                unsealed = builder.build();
            }
            else
            {
                unsealed = unsealConfig(object);
            }
        }

        return unsealed;
    }

    public A2aPushSeal unwrap(
        String token,
        String presented)
    {
        A2aPushSeal seal = token != null ? sealer.unseal(token) : null;

        if (seal != null && (presented == null || seal.expired(clock.millis() / 1000L) || !equal(seal.nonce(), presented) ||
            !policy.permits(seal.url())))
        {
            seal = null;
        }

        return seal;
    }

    public static byte[] bytes(
        JsonValue value)
    {
        final StringWriter text = new StringWriter();
        Json.createWriter(text).write(value.asJsonObject());
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static JsonObject replace(
        JsonObject object,
        String member,
        JsonValue replacement)
    {
        final JsonObjectBuilder builder = Json.createObjectBuilder();
        object.forEach((name, value) -> builder.add(name, member.equals(name) ? replacement : value));
        return builder.build();
    }

    private JsonObject sealConfig(
        String agent,
        String taskId,
        JsonObject config)
    {
        JsonObject sealed = null;

        final String target = string(config, "url");
        final JsonObject authentication = config.get("authentication") instanceof JsonObject member ? member : null;

        if (target != null && policy.permits(target))
        {
            final String nonce = nonce();
            final String sealedUrl = seal(agent, taskId, target, string(config, "token"), authentication, nonce);

            if (sealedUrl != null && sealedUrl.length() <= maxLength)
            {
                final JsonObjectBuilder builder = Json.createObjectBuilder();
                config.forEach((name, value) ->
                {
                    switch (name)
                    {
                    case "url":
                        builder.add(name, sealedUrl);
                        break;
                    case "token":
                        builder.add(name, nonce);
                        break;
                    case "authentication":
                        break;
                    default:
                        builder.add(name, value);
                        break;
                    }
                });

                if (!config.containsKey("token"))
                {
                    builder.add("token", nonce);
                }

                sealed = builder.build();
            }
        }

        return sealed;
    }

    private String seal(
        String agent,
        String taskId,
        String target,
        String token,
        JsonObject authentication)
    {
        return seal(agent, taskId, target, token, authentication, nonce());
    }

    private String seal(
        String agent,
        String taskId,
        String target,
        String token,
        JsonObject authentication,
        String nonce)
    {
        final String sealed = sealer.seal(new A2aPushSeal(agent != null ? agent : "", taskId, target, token, authentication,
            clock.millis() / 1000L + ttlSeconds, VERSION, nonce));

        return sealed != null ? url + "/" + sealed : null;
    }

    private JsonObject unsealConfig(
        JsonObject config)
    {
        JsonObject unsealed = config;

        final String sealedUrl = string(config, "url");

        if (sealedUrl != null && sealedUrl.startsWith(url + "/"))
        {
            final A2aPushSeal seal = sealer.unseal(sealedUrl.substring(url.length() + 1));

            if (seal != null)
            {
                final JsonObjectBuilder builder = Json.createObjectBuilder();
                config.forEach((name, value) ->
                {
                    switch (name)
                    {
                    case "url":
                        builder.add(name, seal.url());
                        break;
                    case "token":
                        if (seal.token() != null)
                        {
                            builder.add(name, seal.token());
                        }
                        break;
                    default:
                        builder.add(name, value);
                        break;
                    }
                });

                if (seal.authentication() != null)
                {
                    builder.add("authentication", seal.authentication());
                }

                unsealed = builder.build();
            }
        }

        return unsealed;
    }

    private String nonce()
    {
        final byte[] bytes = new byte[NONCE_LENGTH];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean equal(
        String expected,
        String presented)
    {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8));
    }

    private static String string(
        JsonObject object,
        String name)
    {
        return object.get(name) instanceof JsonString value ? value.getString() : null;
    }
}
