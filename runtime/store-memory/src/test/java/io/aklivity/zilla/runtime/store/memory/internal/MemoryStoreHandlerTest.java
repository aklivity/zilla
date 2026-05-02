/*
 * Copyright 2021-2024 Aklivity Inc
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import org.agrona.collections.MutableReference;
import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.config.StoreConfig;
import io.aklivity.zilla.runtime.engine.store.StoreHandler;

public class MemoryStoreHandlerTest
{
    private StoreHandler handler;

    @Before
    public void setUp()
    {
        handler = new MemoryStore(new EngineConfiguration())
            .supply(null)
            .attach(StoreConfig.builder()
                .namespace("test")
                .name("memory0")
                .type("memory")
                .build());
    }

    @Test
    public void shouldReturnNullForMissingKey()
    {
        final MutableReference<String> result = new MutableReference<>();
        handler.get("missing", (k, v) -> result.ref = v);
        assertThat(result.ref, nullValue());
    }

    @Test
    public void shouldPutAndGet()
    {
        handler.put("key", "value", Long.MAX_VALUE, v -> {});

        final MutableReference<String> result = new MutableReference<>();
        handler.get("key", (k, v) -> result.ref = v);
        assertThat(result.ref, equalTo("value"));
    }

    @Test
    public void shouldPutIfAbsentWhenKeyMissing()
    {
        final MutableReference<String> existing = new MutableReference<>();
        handler.putIfAbsent("key", "value", Long.MAX_VALUE, v -> existing.ref = v);
        assertThat(existing.ref, nullValue());

        final MutableReference<String> result = new MutableReference<>();
        handler.get("key", (k, v) -> result.ref = v);
        assertThat(result.ref, equalTo("value"));
    }

    @Test
    public void shouldPutIfAbsentReturnExistingWhenKeyPresent()
    {
        handler.put("key", "original", Long.MAX_VALUE, v -> {});

        final MutableReference<String> existing = new MutableReference<>();
        handler.putIfAbsent("key", "new", Long.MAX_VALUE, v -> existing.ref = v);
        assertThat(existing.ref, equalTo("original"));

        final MutableReference<String> result = new MutableReference<>();
        handler.get("key", (k, v) -> result.ref = v);
        assertThat(result.ref, equalTo("original"));
    }

    @Test
    public void shouldDelete()
    {
        handler.put("key", "value", Long.MAX_VALUE, v -> {});
        handler.delete("key", v -> {});

        final MutableReference<String> result = new MutableReference<>();
        handler.get("key", (k, v) -> result.ref = v);
        assertThat(result.ref, nullValue());
    }

    @Test
    public void shouldGetAndDelete()
    {
        handler.put("key", "value", Long.MAX_VALUE, v -> {});

        final MutableReference<String> removed = new MutableReference<>();
        handler.getAndDelete("key", v -> removed.ref = v);
        assertThat(removed.ref, equalTo("value"));

        final MutableReference<String> result = new MutableReference<>();
        handler.get("key", (k, v) -> result.ref = v);
        assertThat(result.ref, nullValue());
    }

    @Test
    public void shouldReturnNullGetAndDeleteForMissingKey()
    {
        final MutableReference<String> removed = new MutableReference<>();
        handler.getAndDelete("missing", v -> removed.ref = v);
        assertThat(removed.ref, nullValue());
    }

    @Test
    public void shouldExpireEntry()
    {
        handler.put("key", "value", -1L, v -> {});

        final MutableReference<String> result = new MutableReference<>();
        handler.get("key", (k, v) -> result.ref = v);
        assertThat(result.ref, nullValue());
    }
}
