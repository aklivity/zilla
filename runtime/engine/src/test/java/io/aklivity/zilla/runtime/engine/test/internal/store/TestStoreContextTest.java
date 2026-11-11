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
package io.aklivity.zilla.runtime.engine.test.internal.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.config.engine.GenericStoreConfig;
import io.aklivity.zilla.config.engine.StoreConfig;
import io.aklivity.zilla.config.engine.test.internal.store.config.TestStoreOptionsConfig;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;

public class TestStoreContextTest
{
    @Test
    public void shouldDetachWhenNoAssertionsConfigured()
    {
        TestStoreContext context = new TestStore(new Configuration()).supply(mock(EngineContext.class));
        StoreConfig store = newStore(TestStoreOptionsConfig.builder().build());

        context.attach(store);
        context.detach(store);
    }

    @Test
    public void shouldDetachWhenAllExpectedOperationsConsumed()
    {
        TestStoreContext context = new TestStore(new Configuration()).supply(mock(EngineContext.class));
        StoreConfig store = newStore(TestStoreOptionsConfig.builder()
            .assertion("put", "key", "value", null)
            .build());

        TestStoreHandler handler = context.attach(store);
        handler.put("key", "value", null, v -> {});
        context.detach(store);
    }

    @Test
    public void shouldFailDetachWhenExpectedOperationsMissing()
    {
        TestStoreContext context = new TestStore(new Configuration()).supply(mock(EngineContext.class));
        StoreConfig store = newStore(TestStoreOptionsConfig.builder()
            .assertion("put", "key", "value", Duration.ofMinutes(1))
            .assertion("delete", "key", null, null)
            .build());

        TestStoreHandler handler = context.attach(store);
        handler.put("key", "value", Duration.ofMinutes(1), v -> {});

        AssertionError error = assertThrows(AssertionError.class, () -> context.detach(store));

        assertEquals("missing 1 expected store operations", error.getMessage());
    }

    @Test
    public void shouldDetachWhenEmptyAssertionsExpectNoOperations()
    {
        TestStoreContext context = new TestStore(new Configuration()).supply(mock(EngineContext.class));
        StoreConfig store = newStore(TestStoreOptionsConfig.builder().assertions(List.of()).build());

        context.attach(store);
        context.detach(store);
    }

    private static StoreConfig newStore(
        TestStoreOptionsConfig options)
    {
        return GenericStoreConfig.builder()
            .namespace("test")
            .name("store0")
            .type("test")
            .options(options)
            .build();
    }
}
