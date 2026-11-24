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
package io.aklivity.zilla.runtime.store.memory.internal;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.Closeable;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.engine.StoreConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.store.StoreContext;
import io.aklivity.zilla.runtime.engine.store.StoreHandler;

public class MemoryStoreContextTest
{
    private final StoreConfig config = mock(StoreConfig.class);
    private final MemoryStore store = new MemoryStore(new MemoryStoreConfiguration());

    private StoreContext detaching;
    private StoreContext remaining;

    @Before
    public void init()
    {
        detaching = store.supply(newEngineContext());
        remaining = store.supply(newEngineContext());
    }

    @Test
    public void shouldNotNotifyWatchersOfDetachedContext() throws Exception
    {
        final List<String> detachedValues = new ArrayList<>();
        final List<String> remainingValues = new ArrayList<>();

        final StoreHandler detachingHandler = detaching.attach(config);
        final StoreHandler remainingHandler = remaining.attach(config);
        detachingHandler.watch("key", (k, v) -> detachedValues.add(v));
        remainingHandler.watch("key", (k, v) -> remainingValues.add(v));

        detaching.detach(config);
        remainingHandler.put("key", "value", Duration.ofSeconds(1), error -> {});

        assertEquals(List.of(), detachedValues);
        assertEquals(List.of("value"), remainingValues);
    }

    @Test
    public void shouldCloseWatchAfterDetach() throws Exception
    {
        final StoreHandler detachingHandler = detaching.attach(config);
        final StoreHandler remainingHandler = remaining.attach(config);
        final List<String> remainingValues = new ArrayList<>();
        final Closeable watch = remainingHandler.watch("key", (k, v) -> remainingValues.add(v));
        detachingHandler.watch("key", (k, v) -> {});

        detaching.detach(config);
        watch.close();
        watch.close();
        remainingHandler.put("key", "value", Duration.ofSeconds(1), error -> {});

        assertEquals(List.of(), remainingValues);
    }

    private static EngineContext newEngineContext()
    {
        final EngineContext context = mock(EngineContext.class);
        when(context.clock()).thenReturn(Clock.systemUTC());
        doAnswer(invocation ->
        {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(context).dispatch(any(Runnable.class));
        return context;
    }
}
