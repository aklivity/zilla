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
package io.aklivity.zilla.runtime.guard.x509.internal;

import static io.aklivity.zilla.specs.guard.x509.certificates.X509Certificates.PARTNER_CHAIN;
import static io.aklivity.zilla.specs.guard.x509.certificates.X509Certificates.PLATFORM_CHAIN;
import static java.util.function.Function.identity;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.function.LongFunction;
import java.util.function.UnaryOperator;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.engine.GenericGuardConfig;
import io.aklivity.zilla.config.engine.GuardConfig;
import io.aklivity.zilla.config.engine.GuardedConfig;
import io.aklivity.zilla.config.guard.x509.X509OptionsConfig;
import io.aklivity.zilla.runtime.common.lang.util.function.LongObjectBiFunction;
import io.aklivity.zilla.runtime.common.lang.util.function.LongObjectPredicate;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.guard.Guard;
import io.aklivity.zilla.runtime.engine.guard.GuardContext;
import io.aklivity.zilla.runtime.engine.guard.GuardFactory;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;

public class X509GuardTest
{
    private EngineContext engine;

    @Before
    public void init()
    {
        engine = mock(EngineContext.class);
        when(engine.index()).thenReturn(0);
        when(engine.supplyAuthorizedId()).thenReturn(1L);
        when(engine.clock()).thenReturn(mock(Clock.class));
        when(engine.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
    }

    @Test
    public void shouldResolveName()
    {
        Guard guard = GuardFactory.instantiate().create("x509", new Configuration());

        assertEquals("x509", guard.name());
    }

    @Test
    public void shouldNotVerifyMissingContext()
    {
        Guard guard = GuardFactory.instantiate().create("x509", new Configuration());

        LongObjectPredicate<UnaryOperator<String>> verifier = guard.verifier(s -> 0, guarded());

        assertFalse(verifier.test(1L, UnaryOperator.identity()));
    }

    @Test
    public void shouldNotVerifyMissingHandler()
    {
        Guard guard = GuardFactory.instantiate().create("x509", new Configuration());

        guard.supply(engine);

        LongObjectPredicate<UnaryOperator<String>> verifier = guard.verifier(s -> 0, guarded());

        assertFalse(verifier.test(1L, UnaryOperator.identity()));
    }

    @Test
    public void shouldNotVerifyMissingSession()
    {
        Guard guard = GuardFactory.instantiate().create("x509", new Configuration());

        GuardContext context = guard.supply(engine);
        context.attach(config());

        LongObjectPredicate<UnaryOperator<String>> verifier = guard.verifier(s -> 0, guarded());

        assertFalse(verifier.test(1L, UnaryOperator.identity()));
    }

    @Test
    public void shouldNotVerifyRolesWhenInsufficient()
    {
        Guard guard = GuardFactory.instantiate().create("x509", new Configuration());

        GuardContext context = guard.supply(engine);
        GuardHandler handler = context.attach(config());

        LongObjectPredicate<UnaryOperator<String>> verifier = guard.verifier(s -> 0, guarded());

        long sessionId = handler.reauthorize(0L, 0L, 101L, PARTNER_CHAIN);

        assertFalse(verifier.test(sessionId, UnaryOperator.identity()));
    }

    @Test
    public void shouldVerifyRoles()
    {
        Guard guard = GuardFactory.instantiate().create("x509", new Configuration());

        GuardContext context = guard.supply(engine);
        GuardHandler handler = context.attach(config());

        LongObjectPredicate<UnaryOperator<String>> verifier = guard.verifier(s -> 0, guarded());

        long sessionId = handler.reauthorize(0L, 0L, 101L, PLATFORM_CHAIN);

        assertTrue(verifier.test(sessionId, UnaryOperator.identity()));
    }

    @Test
    public void shouldVerifyWhenIndexDiffers()
    {
        Guard guard = GuardFactory.instantiate().create("x509", new Configuration());

        GuardContext context = guard.supply(engine);

        GuardConfig config = config();
        config.id = 0x11L;
        GuardHandler handler = context.attach(config);

        GuardedConfig guarded = guarded();
        guarded.id = config.id;

        LongObjectPredicate<UnaryOperator<String>> verifier = guard.verifier(id -> (int) (id >> 4), guarded);

        long sessionId = handler.reauthorize(0L, 0L, 101L, PLATFORM_CHAIN);

        assertTrue(verifier.test(sessionId, UnaryOperator.identity()));
    }

    @Test
    public void shouldIdentifyAndResolveAttribute()
    {
        Guard guard = GuardFactory.instantiate().create("x509", new Configuration());

        GuardContext context = guard.supply(engine);
        GuardHandler handler = context.attach(config());

        GuardedConfig guarded = guarded();

        LongFunction<String> identifier = guard.identifier(s -> 0, guarded);
        LongObjectBiFunction<String, String> attributor = guard.attributor(s -> 0, guarded);

        long sessionId = handler.reauthorize(0L, 0L, 101L, PLATFORM_CHAIN);

        assertEquals("platform.example.com", identifier.apply(sessionId));
        assertEquals("Example Inc", attributor.apply(sessionId, "organization"));
    }

    private static GuardedConfig guarded()
    {
        return GuardedConfig.builder()
            .inject(identity())
            .name("test0")
            .role("internal")
            .build();
    }

    private static GuardConfig config()
    {
        return GenericGuardConfig.builder()
            .inject(identity())
            .namespace("test")
            .name("test0")
            .type("x509")
            .options(X509OptionsConfig::builder)
                .inject(identity())
                .identity("subject.cn")
                .attribute("organization", "subject.o")
                .match("internal")
                    .field("issuer.cn", "Internal CA")
                    .build()
                .build()
            .build();
    }
}
