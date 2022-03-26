/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.runtime.guard.jwt.internal;

import static io.aklivity.zilla.runtime.guard.jwt.internal.JwtGuardHandlerTest.sign;
import static io.aklivity.zilla.runtime.guard.jwt.internal.keys.JwtKeyConfigs.RFC7515_RS256_CONFIG;
import static io.aklivity.zilla.specs.guard.jwt.keys.JwtKeys.RFC7515_RS256;
import static java.time.Duration.ofSeconds;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.function.LongPredicate;

import org.jose4j.jwt.JwtClaims;
import org.junit.Test;
import org.mockito.Mockito;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.GuardConfig;
import io.aklivity.zilla.runtime.engine.config.GuardedConfig;
import io.aklivity.zilla.runtime.engine.guard.Guard;
import io.aklivity.zilla.runtime.engine.guard.GuardContext;
import io.aklivity.zilla.runtime.engine.guard.GuardFactory;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;
import io.aklivity.zilla.runtime.guard.jwt.internal.config.JwtOptionsConfig;

public class JwtGuardTest
{
    @Test
    public void shouldNotVerifyMissingContext() throws Exception
    {
        GuardedConfig guarded = new GuardedConfig("test0", asList("read:stream", "write:stream"));

        Configuration config = new Configuration();
        GuardFactory factory = GuardFactory.instantiate();
        Guard guard = factory.create("jwt", config);

        LongPredicate verifier = guard.verifier(s -> 0, guarded);

        assertFalse(verifier.test(1L));
    }

    @Test
    public void shouldNotVerifyMissingHandler() throws Exception
    {
        EngineContext engine = Mockito.mock(EngineContext.class);

        when(engine.index()).thenReturn(0);

        GuardedConfig guarded = new GuardedConfig("test0", asList("read:stream", "write:stream"));

        Configuration config = new Configuration();
        GuardFactory factory = GuardFactory.instantiate();
        Guard guard = factory.create("jwt", config);

        guard.supply(engine);

        LongPredicate verifier = guard.verifier(s -> 0, guarded);

        assertFalse(verifier.test(1L));
    }

    @Test
    public void shouldNotVerifyMissingSession() throws Exception
    {
        EngineContext engine = Mockito.mock(EngineContext.class);

        when(engine.index()).thenReturn(0);

        GuardedConfig guarded = new GuardedConfig("test0", asList("read:stream", "write:stream"));

        Configuration config = new Configuration();
        GuardFactory factory = GuardFactory.instantiate();
        Guard guard = factory.create("jwt", config);

        GuardContext context = guard.supply(engine);
        context.attach(new GuardConfig("test0", "jwt", new JwtOptionsConfig(null, null, null, null)));

        LongPredicate verifier = guard.verifier(s -> 0, guarded);

        assertFalse(verifier.test(1L));
    }

    @Test
    public void shouldNotVerifyRolesWhenInsufficient() throws Exception
    {
        EngineContext engine = Mockito.mock(EngineContext.class);

        when(engine.index()).thenReturn(0);

        GuardedConfig guarded = new GuardedConfig("test0", asList("read:stream", "write:stream"));

        Configuration config = new Configuration();
        GuardFactory factory = GuardFactory.instantiate();
        Guard guard = factory.create("jwt", config);

        GuardContext context = guard.supply(engine);

        Duration challenge = ofSeconds(3L);
        JwtOptionsConfig options =
                new JwtOptionsConfig("test issuer", "testAudience", singletonList(RFC7515_RS256_CONFIG), challenge);
        GuardHandler handler = context.attach(new GuardConfig("test0", "jwt", options));

        LongPredicate verifier = guard.verifier(s -> 0, guarded);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("sub", "testSubject");
        claims.setClaim("exp", now.getEpochSecond() + 10L);
        claims.setClaim("scope", "read:stream");

        String token = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long authorizedId = handler.reauthorize(101L, token);

        assertFalse(verifier.test(authorizedId));
    }

    @Test
    public void shouldVerifyRolesWhenExact() throws Exception
    {
        EngineContext engine = Mockito.mock(EngineContext.class);

        when(engine.index()).thenReturn(0);
        when(engine.supplyAuthorizedId()).thenReturn(1L);

        GuardedConfig guarded = new GuardedConfig("test0", asList("read:stream", "write:stream"));

        Configuration config = new Configuration();
        GuardFactory factory = GuardFactory.instantiate();
        Guard guard = factory.create("jwt", config);

        GuardContext context = guard.supply(engine);

        Duration challenge = ofSeconds(3L);
        JwtOptionsConfig options =
                new JwtOptionsConfig("test issuer", "testAudience", singletonList(RFC7515_RS256_CONFIG), challenge);
        GuardHandler handler = context.attach(new GuardConfig("test0", "jwt", options));

        LongPredicate verifier = guard.verifier(s -> 0, guarded);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("sub", "testSubject");
        claims.setClaim("exp", now.getEpochSecond() + 10L);
        claims.setClaim("scope", "read:stream write:stream");

        String token = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionId = handler.reauthorize(101L, token);

        assertTrue(verifier.test(sessionId));
    }

    @Test
    public void shouldVerifyRolesWhenSuperset() throws Exception
    {
        EngineContext engine = Mockito.mock(EngineContext.class);

        when(engine.index()).thenReturn(0);
        when(engine.supplyAuthorizedId()).thenReturn(1L);

        GuardedConfig guarded = new GuardedConfig("test0", asList("read:stream"));

        Configuration config = new Configuration();
        GuardFactory factory = GuardFactory.instantiate();
        Guard guard = factory.create("jwt", config);

        GuardContext context = guard.supply(engine);

        Duration challenge = ofSeconds(3L);
        JwtOptionsConfig options =
                new JwtOptionsConfig("test issuer", "testAudience", singletonList(RFC7515_RS256_CONFIG), challenge);
        GuardHandler handler = context.attach(new GuardConfig("test0", "jwt", options));

        LongPredicate verifier = guard.verifier(s -> 0, guarded);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("sub", "testSubject");
        claims.setClaim("exp", now.getEpochSecond() + 10L);
        claims.setClaim("scope", "read:stream write:stream");

        String token = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionId = handler.reauthorize(101L, token);

        assertTrue(verifier.test(sessionId));
    }

    @Test
    public void shouldVerifyRolesWhenEmpty() throws Exception
    {
        EngineContext engine = Mockito.mock(EngineContext.class);

        when(engine.index()).thenReturn(0);
        when(engine.supplyAuthorizedId()).thenReturn(1L);

        GuardedConfig guarded = new GuardedConfig("test0", asList());

        Configuration config = new Configuration();
        GuardFactory factory = GuardFactory.instantiate();
        Guard guard = factory.create("jwt", config);

        GuardContext context = guard.supply(engine);

        Duration challenge = ofSeconds(3L);
        JwtOptionsConfig options =
                new JwtOptionsConfig("test issuer", "testAudience", singletonList(RFC7515_RS256_CONFIG), challenge);
        GuardHandler handler = context.attach(new GuardConfig("test0", "jwt", options));

        LongPredicate verifier = guard.verifier(s -> 0, guarded);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("sub", "testSubject");
        claims.setClaim("exp", now.getEpochSecond() + 10L);
        claims.setClaim("scope", "read:stream write:stream");

        String token = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionId = handler.reauthorize(101L, token);

        assertTrue(verifier.test(sessionId));
    }

    @Test
    public void shouldVerifyWhenIndexDiffers() throws Exception
    {
        EngineContext engine = Mockito.mock(EngineContext.class);

        when(engine.index()).thenReturn(0);
        when(engine.supplyAuthorizedId()).thenReturn(0x01L);

        GuardFactory factory = GuardFactory.instantiate();
        Guard guard = factory.create("jwt", new Configuration());

        GuardContext context = guard.supply(engine);

        Duration challenge = ofSeconds(3L);
        JwtOptionsConfig options =
                new JwtOptionsConfig("test issuer", "testAudience", singletonList(RFC7515_RS256_CONFIG), challenge);
        GuardConfig config = new GuardConfig("test0", "jwt", options);
        config.id = 0x11L;
        GuardHandler handler = context.attach(config);

        GuardedConfig guarded = new GuardedConfig("test0", asList());
        guarded.id = config.id;
        LongPredicate verifier = guard.verifier(id -> (int)(id >> 4), guarded);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("sub", "testSubject");
        claims.setClaim("exp", now.getEpochSecond() + 10L);
        claims.setClaim("scope", "read:stream write:stream");

        String token = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionId = handler.reauthorize(101L, token);

        assertTrue(verifier.test(sessionId));
    }
}
