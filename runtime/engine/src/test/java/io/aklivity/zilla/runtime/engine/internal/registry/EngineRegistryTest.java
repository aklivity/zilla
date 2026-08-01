/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.internal.registry;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.util.concurrent.CompletionException;

import org.junit.Test;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.KindConfig;
import io.aklivity.zilla.config.engine.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.binding.BindingContext;

public class EngineRegistryTest
{
    @Test
    public void shouldPropagateExceptionWhenAttachNowFails()
    {
        RuntimeException attachFailure = new RuntimeException("boom");

        NamespaceConfig namespace = NamespaceConfig.builder()
            .name("test")
            .binding()
                .name("test0")
                .type("test")
                .kind(KindConfig.CLIENT)
                .build()
            .build();

        EngineRegistry registry = new EngineRegistry(
            type -> new BindingContext()
            {
                @Override
                public io.aklivity.zilla.runtime.engine.binding.BindingHandler attach(
                    BindingConfig binding)
                {
                    throw attachFailure;
                }
            },
            type -> null,
            type -> null,
            type -> null,
            name -> null,
            type -> null,
            type -> null,
            String::hashCode,
            EngineRegistryTest::noop,
            EngineRegistryTest::noop,
            (kind, value1, value2, value3, kindConfig) -> EngineRegistryTest::noop,
            EngineRegistryTest::noop,
            null,
            EngineRegistryTest::noop);

        try
        {
            registry.attachNow(namespace);
            fail("expected attachNow to propagate the attach failure");
        }
        catch (CompletionException ex)
        {
            assertSame(attachFailure, ex.getCause());
        }
    }

    private static void noop(
        long id)
    {
    }

    private static void noop(
        NamespaceConfig namespace)
    {
    }
}
