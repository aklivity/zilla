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

import java.util.List;

import jakarta.json.Json;

import org.junit.Test;

import io.aklivity.zilla.config.engine.GenericVaultConfig;
import io.aklivity.zilla.config.engine.VaultConfig;
import io.aklivity.zilla.config.engine.test.internal.vault.config.TestVaultOptionsConfig;
import io.aklivity.zilla.config.engine.test.internal.vault.config.TestVaultOptionsConfigBuilder;
import io.aklivity.zilla.runtime.engine.test.internal.vault.TestVaultHandler;

public class A2aPushSealerTest
{
    private static final String ACTIVE_KEY_PEM =
        "-----BEGIN SECRET KEY-----\n" +
        "jOl3tg1ZDuqTrzB7WOVaUOEoezst8sz/ywPPbsGE8bA=\n" +
        "-----END SECRET KEY-----";

    private static final String RETIRED_KEY_PEM =
        "-----BEGIN SECRET KEY-----\n" +
        "J5XxCYN3xHEx4qzx6juAdpYPDBhT11KBg2Y/LJz6AIE=\n" +
        "-----END SECRET KEY-----";

    private static final A2aPushSeal SEAL = new A2aPushSeal(
        "weather",
        "t-1",
        "https://client.example.com/webhook",
        "tok-1",
        Json.createObjectBuilder().add("scheme", "Bearer").add("credentials", "cred-1").build(),
        1000L,
        "1.0",
        "nonce-1");

    @Test
    public void shouldRoundTrip()
    {
        final A2aPushSealer sealer = sealer("a2a-push", "a2a-push");

        final String token = sealer.seal(SEAL);

        assertThat(token, not(nullValue()));
        assertThat(token.matches("[A-Za-z0-9_-]+"), equalTo(true));
        assertThat(sealer.unseal(token), equalTo(SEAL));
    }

    @Test
    public void shouldRoundTripWithoutOptionalMembers()
    {
        final A2aPushSealer sealer = sealer("a2a-push", "a2a-push");
        final A2aPushSeal minimal = new A2aPushSeal("weather", null, "https://client.example.com/webhook", null, null,
            1000L, "1.0", "nonce-1");

        assertThat(sealer.unseal(sealer.seal(minimal)), equalTo(minimal));
    }

    @Test
    public void shouldUnsealWithSecondInstanceOfSameKey()
    {
        final String token = sealer("a2a-push", "a2a-push").seal(SEAL);

        assertThat(sealer("a2a-push", "a2a-push").unseal(token), equalTo(SEAL));
    }

    @Test
    public void shouldNotUnsealTamperedToken()
    {
        final A2aPushSealer sealer = sealer("a2a-push", "a2a-push");
        final String token = sealer.seal(SEAL);
        final char flipped = token.charAt(token.length() / 2) == 'A' ? 'B' : 'A';
        final String tampered = token.substring(0, token.length() / 2) + flipped + token.substring(token.length() / 2 + 1);

        assertThat(sealer.unseal(tampered), nullValue());
    }

    @Test
    public void shouldNotUnsealGarbledToken()
    {
        final A2aPushSealer sealer = sealer("a2a-push", "a2a-push");

        assertThat(sealer.unseal("not base64 !!"), nullValue());
        assertThat(sealer.unseal(""), nullValue());
        assertThat(sealer.unseal("AAAA"), nullValue());
    }

    @Test
    public void shouldNotUnsealTokenSealedUnderRetiredKey()
    {
        final String token = sealer("a2a-push-v1", "a2a-push-v1", "a2a-push-v2").seal(SEAL);

        assertThat(sealer("a2a-push-v2", "a2a-push-v2").unseal(token), nullValue());
    }

    @Test
    public void shouldNotSealWhenKeyUnresolved()
    {
        assertThat(sealer("a2a-push", "other").seal(SEAL), nullValue());
    }

    @Test
    public void shouldExpire()
    {
        assertThat(SEAL.expired(1000L), equalTo(false));
        assertThat(SEAL.expired(1001L), equalTo(true));
    }

    @Test
    public void shouldBoundSealedLengthByPayload()
    {
        final A2aPushSealer sealer = sealer("a2a-push", "a2a-push");
        final String token = sealer.seal(SEAL);
        final String longer = sealer.seal(new A2aPushSeal("weather", "t-1", "https://client.example.com/" + "x".repeat(2048),
            null, null, 1000L, "1.0", "nonce-1"));

        assertThat(token, notNullValue());
        assertThat(longer.length() > 2048, equalTo(true));
    }

    private static A2aPushSealer sealer(
        String key,
        String alias,
        String... others)
    {
        final TestVaultOptionsConfigBuilder<TestVaultOptionsConfig> options =
            TestVaultOptionsConfig.builder().wrap(alias, ACTIVE_KEY_PEM);
        for (String other : others)
        {
            options.wrap(other, other.equals(alias) ? ACTIVE_KEY_PEM : RETIRED_KEY_PEM);
        }

        final VaultConfig vault = GenericVaultConfig.builder()
            .namespace("test")
            .name("secrets0")
            .type("test")
            .options(options.build())
            .build();

        return new A2aPushSealer(new TestVaultHandler(vault).initSecretKeys(List.of(alias)).getSecretKeyManager(), key);
    }
}
