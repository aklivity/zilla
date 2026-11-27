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

package io.aklivity.zilla.runtime.binding.a2a.internal.stream;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import io.aklivity.zilla.config.engine.GenericVaultConfig;
import io.aklivity.zilla.config.engine.VaultConfig;
import io.aklivity.zilla.config.engine.test.internal.vault.config.TestVaultOptionsConfig;
import io.aklivity.zilla.runtime.binding.a2a.internal.push.A2aPushSeal;
import io.aklivity.zilla.runtime.binding.a2a.internal.push.A2aPushSealer;
import io.aklivity.zilla.runtime.engine.test.internal.vault.TestVaultHandler;

public final class A2aPushFixtures
{
    static final String BASE = "http://localhost:8080/a2a/push";
    static final String CONFIG_URL = "https://client.example.com/webhook";
    static final String HOOK_URL = "https://client.example.com/hooks/1";
    static final String ACTIVE_KEY = "a2a-push";
    static final String RETIRED_KEY = "a2a-push-retired";
    static final String NONCE = "nonce-1";

    private static final String ACTIVE_KEY_PEM =
        "-----BEGIN SECRET KEY-----\n" +
        "jOl3tg1ZDuqTrzB7WOVaUOEoezst8sz/ywPPbsGE8bA=\n" +
        "-----END SECRET KEY-----";

    private static final String RETIRED_KEY_PEM =
        "-----BEGIN SECRET KEY-----\n" +
        "J5XxCYN3xHEx4qzx6juAdpYPDBhT11KBg2Y/LJz6AIE=\n" +
        "-----END SECRET KEY-----";

    private static final JsonObject AUTHENTICATION =
        Json.createObjectBuilder().add("scheme", "Bearer").add("credentials", "cred-1").build();

    static String token(
        String key,
        String url,
        String taskId,
        long expiresInSeconds)
    {
        final VaultConfig vault = GenericVaultConfig.builder()
            .namespace("test")
            .name("secrets0")
            .type("test")
            .options(TestVaultOptionsConfig.builder()
                .wrap(ACTIVE_KEY, ACTIVE_KEY_PEM)
                .wrap(RETIRED_KEY, RETIRED_KEY_PEM)
                .build())
            .build();

        final A2aPushSealer sealer = new A2aPushSealer(
            new TestVaultHandler(vault).initSecretKeys(List.of(key)).getSecretKeyManager(), key);

        return sealer.seal(new A2aPushSeal("weather", taskId, url, "tok-1", AUTHENTICATION,
            Instant.now().getEpochSecond() + expiresInSeconds, "1.0", NONCE));
    }

    static String token(
        String url)
    {
        return token(ACTIVE_KEY, url, "t-1", 3600L);
    }

    static String tampered(
        String token)
    {
        final int middle = token.length() / 2;
        return token.substring(0, middle) + (token.charAt(middle) == 'A' ? 'B' : 'A') + token.substring(middle + 1);
    }

    public static InetAddress[] resolveHost(
        String host) throws UnknownHostException
    {
        return switch (host)
        {
        case "client.example.com" -> address(host, 93, 184, 216, 34);
        case "internal.example.com" -> address(host, 10, 0, 0, 5);
        default -> literal(host);
        };
    }

    private static InetAddress[] literal(
        String host) throws UnknownHostException
    {
        if (!host.matches("[0-9.]+|\\[?[0-9a-f:]+\\]?"))
        {
            throw new UnknownHostException(host);
        }

        return new InetAddress[] {InetAddress.getByName(host)};
    }

    private static InetAddress[] address(
        String host,
        int... octets) throws UnknownHostException
    {
        final byte[] bytes = new byte[octets.length];
        for (int i = 0; i < octets.length; i++)
        {
            bytes[i] = (byte) octets[i];
        }
        return new InetAddress[] {InetAddress.getByAddress(host, bytes)};
    }

    private A2aPushFixtures()
    {
    }
}
