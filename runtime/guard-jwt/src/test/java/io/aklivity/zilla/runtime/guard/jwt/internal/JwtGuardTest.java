/*
 * Copyright 2021-2023 Aklivity Inc
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
import static java.util.function.Function.identity;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.function.LongFunction;
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
import io.aklivity.zilla.runtime.guard.jwt.config.JwtOptionsConfig;

public class JwtGuardTest
{
    @Test
    public void shouldNotVerifyMissingContext() throws Exception
    {
        GuardedConfig guarded = GuardedConfig.builder()
                .inject(identity())
                .name("test0")
                .role("read:stream")
                .role("write:stream")
                .build();

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

        GuardedConfig guarded = GuardedConfig.builder()
                .inject(identity())
                .name("test0")
                .role("read:stream")
                .role("write:stream")
                .build();

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

        GuardedConfig guarded = GuardedConfig.builder()
                .inject(identity())
                .name("test0")
                .role("read:stream")
                .role("write:stream")
                .build();

        Configuration config = new Configuration();
        GuardFactory factory = GuardFactory.instantiate();
        Guard guard = factory.create("jwt", config);

        GuardContext context = guard.supply(engine);
        context.attach(GuardConfig.builder()
                .inject(identity())
                .name("test0")
                .type("jwt")
                .options(JwtOptionsConfig.builder().build())
                .build());

        LongPredicate verifier = guard.verifier(s -> 0, guarded);

        assertFalse(verifier.test(1L));
    }

    @Test
    public void shouldNotVerifyRolesWhenInsufficient() throws Exception
    {
        EngineContext engine = Mockito.mock(EngineContext.class);

        when(engine.index()).thenReturn(0);

        GuardedConfig guarded = GuardedConfig.builder()
                .name("test0")
                .role("read:stream")
                .role("write:stream")
                .build();

        Configuration config = new Configuration();
        GuardFactory factory = GuardFactory.instantiate();
        Guard guard = factory.create("jwt", config);

        GuardContext context = guard.supply(engine);

        GuardHandler handler = context.attach(GuardConfig.builder()
            .inject(identity())
            .name("test0")
            .type("jwt")
            .options(JwtOptionsConfig::builder)
                .inject(identity())
                .issuer("test issuer")
                .audience("testAudience")
                .key(RFC7515_RS256_CONFIG)
                .challenge(ofSeconds(3L))
                .build()
            .build());

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

        GuardedConfig guarded = GuardedConfig.builder()
                .inject(identity())
                .name("test0")
                .role("read:stream")
                .role("write:stream")
                .build();

        Configuration config = new Configuration();
        GuardFactory factory = GuardFactory.instantiate();
        Guard guard = factory.create("jwt", config);

        GuardContext context = guard.supply(engine);

        GuardHandler handler = context.attach(GuardConfig.builder()
            .inject(identity())
            .name("test0")
            .type("jwt")
            .options(JwtOptionsConfig::builder)
                .inject(identity())
                .issuer("test issuer")
                .audience("testAudience")
                .key(RFC7515_RS256_CONFIG)
                .challenge(ofSeconds(3L))
                .build()
            .build());

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

        GuardedConfig guarded = GuardedConfig.builder()
                .inject(identity())
                .name("test0")
                .role("read:stream")
                .build();

        Configuration config = new Configuration();
        GuardFactory factory = GuardFactory.instantiate();
        Guard guard = factory.create("jwt", config);

        GuardContext context = guard.supply(engine);

        GuardHandler handler = context.attach(GuardConfig.builder()
            .inject(identity())
            .name("test0")
            .type("jwt")
            .options(JwtOptionsConfig::builder)
                .inject(identity())
                .issuer("test issuer")
                .audience("testAudience")
                .key(RFC7515_RS256_CONFIG)
                .challenge(ofSeconds(3L))
                .build()
            .build());

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

        GuardedConfig guarded = GuardedConfig.builder()
                .inject(identity())
                .name("test0")
                .build();

        Configuration config = new Configuration();
        GuardFactory factory = GuardFactory.instantiate();
        Guard guard = factory.create("jwt", config);

        GuardContext context = guard.supply(engine);

        GuardHandler handler = context.attach(GuardConfig.builder()
            .inject(identity())
            .name("test0")
            .type("jwt")
            .options(JwtOptionsConfig::builder)
                .inject(identity())
                .issuer("test issuer")
                .audience("testAudience")
                .key(RFC7515_RS256_CONFIG)
                .challenge(ofSeconds(3L))
                .build()
            .build());

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

        GuardConfig config = GuardConfig.builder()
            .inject(identity())
            .name("test0")
            .type("jwt")
            .options(JwtOptionsConfig::builder)
                .inject(identity())
                .issuer("test issuer")
                .audience("testAudience")
                .key(RFC7515_RS256_CONFIG)
                .challenge(ofSeconds(3L))
                .build()
            .build();
        config.id = 0x11L;
        GuardHandler handler = context.attach(config);

        GuardedConfig guarded = GuardedConfig.builder()
                .name("test0")
                .build();
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

    @Test
    public void shouldIdentify() throws Exception
    {
        EngineContext engine = Mockito.mock(EngineContext.class);

        when(engine.index()).thenReturn(0);
        when(engine.supplyAuthorizedId()).thenReturn(1L);

        GuardedConfig guarded = GuardedConfig.builder()
                .inject(identity())
                .name("test0")
                .build();

        Configuration config = new Configuration();
        GuardFactory factory = GuardFactory.instantiate();
        Guard guard = factory.create("jwt", config);

        GuardContext context = guard.supply(engine);

        GuardHandler handler = context.attach(GuardConfig.builder()
                .inject(identity())
                .name("test0")
                .type("jwt")
                .options(JwtOptionsConfig::builder)
                    .inject(identity())
                    .issuer("test issuer")
                    .audience("testAudience")
                    .key(RFC7515_RS256_CONFIG)
                    .challenge(ofSeconds(3L))
                    .build()
                .build());

        LongFunction<String> identifier = guard.identifier(s -> 0, guarded);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("sub", "testSubject");
        claims.setClaim("exp", now.getEpochSecond() + 10L);
        claims.setClaim("scope", "read:stream write:stream");

        String token = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionId = handler.reauthorize(101L, token);

        assertEquals("testSubject", identifier.apply(sessionId));
    }

    @Test
    public void shouldIdentifyWhenIndexDiffers() throws Exception
    {
        EngineContext engine = Mockito.mock(EngineContext.class);

        when(engine.index()).thenReturn(0);
        when(engine.supplyAuthorizedId()).thenReturn(0x01L);

        GuardFactory factory = GuardFactory.instantiate();
        Guard guard = factory.create("jwt", new Configuration());

        GuardContext context = guard.supply(engine);

        Duration challenge = ofSeconds(3L);
        GuardConfig config = GuardConfig.builder()
            .inject(identity())
            .name("test0")
            .type("jwt")
            .options(JwtOptionsConfig::builder)
                .inject(identity())
                .issuer("test issuer")
                .audience("testAudience")
                .key(RFC7515_RS256_CONFIG)
                .challenge(challenge)
                .build()
            .build();
        config.id = 0x11L;

        GuardedConfig guarded = GuardedConfig.builder()
            .name("test0")
            .build();
        guarded.id = config.id;
        GuardHandler handler = context.attach(config);

        LongFunction<String> identifier = guard.identifier(id -> (int)(id >> 4), guarded);

        Instant now = Instant.now();

        JwtClaims claims = new JwtClaims();
        claims.setClaim("iss", "test issuer");
        claims.setClaim("aud", "testAudience");
        claims.setClaim("sub", "testSubject");
        claims.setClaim("exp", now.getEpochSecond() + 10L);
        claims.setClaim("scope", "read:stream write:stream");

        String token = sign(claims.toJson(), "test", RFC7515_RS256, "RS256");

        long sessionId = handler.reauthorize(101L, token);

        assertEquals("testSubject", identifier.apply(sessionId));
    }
}
