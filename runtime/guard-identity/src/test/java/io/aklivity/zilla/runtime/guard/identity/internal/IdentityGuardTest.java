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
package io.aklivity.zilla.runtime.guard.identity.internal;

import static java.util.function.Function.identity;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

import java.util.function.LongFunction;
import java.util.function.UnaryOperator;

import org.junit.Test;
import org.mockito.Mockito;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.GuardConfig;
import io.aklivity.zilla.runtime.engine.config.GuardedConfig;
import io.aklivity.zilla.runtime.engine.guard.Guard;
import io.aklivity.zilla.runtime.engine.guard.GuardContext;
import io.aklivity.zilla.runtime.engine.guard.GuardFactory;
import io.aklivity.zilla.runtime.engine.util.function.LongObjectBiFunction;
import io.aklivity.zilla.runtime.engine.util.function.LongObjectPredicate;

public class IdentityGuardTest
{
    @Test
    public void shouldNotVerifyMissingContext() throws Exception
    {
        GuardedConfig guarded = GuardedConfig.builder()
            .inject(identity())
            .name("test0")
            .build();

        Configuration config = new Configuration();
        GuardFactory factory = GuardFactory.instantiate();
        Guard guard = factory.create("identity", config);

        LongObjectPredicate<UnaryOperator<String>> verifier = guard.verifier(s -> 0, guarded);

        assertFalse(verifier.test(1L, UnaryOperator.identity()));
    }

    @Test
    public void shouldNotVerifyMissingHandler() throws Exception
    {
        EngineContext engine = Mockito.mock(EngineContext.class);

        when(engine.index()).thenReturn(0);

        GuardedConfig guarded = GuardedConfig.builder()
            .inject(identity())
            .name("test0")
            .build();

        Configuration config = new Configuration();
        GuardFactory factory = GuardFactory.instantiate();
        Guard guard = factory.create("identity", config);

        guard.supply(engine);

        LongObjectPredicate<UnaryOperator<String>> verifier = guard.verifier(s -> 0, guarded);

        assertFalse(verifier.test(1L, UnaryOperator.identity()));
    }

    @Test
    public void shouldNotVerifyMissingSession() throws Exception
    {
        EngineContext engine = Mockito.mock(EngineContext.class);

        when(engine.index()).thenReturn(0);

        GuardedConfig guarded = GuardedConfig.builder()
            .inject(identity())
            .name("test0")
            .build();

        Configuration config = new Configuration();
        GuardFactory factory = GuardFactory.instantiate();
        Guard guard = factory.create("identity", config);

        GuardContext context = guard.supply(engine);
        context.attach(GuardConfig.builder()
            .inject(identity())
            .namespace("test")
            .name("test0")
            .type("identity")
            .build());

        LongObjectPredicate<UnaryOperator<String>> verifier = guard.verifier(s -> 0, guarded);

        assertFalse(verifier.test(1L, UnaryOperator.identity()));

        LongFunction<String> identifier = guard.identifier(id -> (int)(id >> 4), guarded);
        assertNotNull(identifier);

        LongObjectBiFunction<String, String> attributor = guard.attributor(id -> (int)(id >> 4), guarded);
        assertNotNull(attributor);
    }
}
