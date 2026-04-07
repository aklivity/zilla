/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.store.memory.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;

public class MemoryStoreHandlerTest
{
    private MemoryStoreHandler handler;

    @Before
    public void setUp()
    {
        handler = new MemoryStoreHandler(new MemoryStore(new MemoryStoreConfiguration(new Configuration())));
    }

    @Test
    public void shouldReturnNullForMissingKey()
    {
        final AtomicReference<String> result = new AtomicReference<>();
        handler.get("missing", (k, v) -> result.set(v));
        assertThat(result.get(), nullValue());
    }

    @Test
    public void shouldPutAndGet()
    {
        handler.put("key", "value", Long.MAX_VALUE, v -> {});

        final AtomicReference<String> result = new AtomicReference<>();
        handler.get("key", (k, v) -> result.set(v));
        assertThat(result.get(), equalTo("value"));
    }

    @Test
    public void shouldPutIfAbsentWhenKeyMissing()
    {
        final AtomicReference<String> existing = new AtomicReference<>();
        handler.putIfAbsent("key", "value", Long.MAX_VALUE, v -> existing.set(v));
        assertThat(existing.get(), nullValue());

        final AtomicReference<String> result = new AtomicReference<>();
        handler.get("key", (k, v) -> result.set(v));
        assertThat(result.get(), equalTo("value"));
    }

    @Test
    public void shouldPutIfAbsentReturnExistingWhenKeyPresent()
    {
        handler.put("key", "original", Long.MAX_VALUE, v -> {});

        final AtomicReference<String> existing = new AtomicReference<>();
        handler.putIfAbsent("key", "new", Long.MAX_VALUE, v -> existing.set(v));
        assertThat(existing.get(), equalTo("original"));

        final AtomicReference<String> result = new AtomicReference<>();
        handler.get("key", (k, v) -> result.set(v));
        assertThat(result.get(), equalTo("original"));
    }

    @Test
    public void shouldDelete()
    {
        handler.put("key", "value", Long.MAX_VALUE, v -> {});
        handler.delete("key", v -> {});

        final AtomicReference<String> result = new AtomicReference<>();
        handler.get("key", (k, v) -> result.set(v));
        assertThat(result.get(), nullValue());
    }

    @Test
    public void shouldGetAndDelete()
    {
        handler.put("key", "value", Long.MAX_VALUE, v -> {});

        final AtomicReference<String> removed = new AtomicReference<>();
        handler.getAndDelete("key", v -> removed.set(v));
        assertThat(removed.get(), equalTo("value"));

        final AtomicReference<String> result = new AtomicReference<>();
        handler.get("key", (k, v) -> result.set(v));
        assertThat(result.get(), nullValue());
    }

    @Test
    public void shouldReturnNullGetAndDeleteForMissingKey()
    {
        final AtomicReference<String> removed = new AtomicReference<>();
        handler.getAndDelete("missing", v -> removed.set(v));
        assertThat(removed.get(), nullValue());
    }

    @Test
    public void shouldExpireEntry()
    {
        handler.put("key", "value", -1L, v -> {});

        final AtomicReference<String> result = new AtomicReference<>();
        handler.get("key", (k, v) -> result.set(v));
        assertThat(result.get(), nullValue());
    }
}
