/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.test.internal.binding.config;

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public final class TestBindingOptionsConfig extends OptionsConfig
{
    public final String mode;
    public final List<String> catalogs;
    public final List<Event> events;

    public static TestBindingOptionsConfigBuilder<TestBindingOptionsConfig> builder()
    {
        return new TestBindingOptionsConfigBuilder<>(TestBindingOptionsConfig.class::cast);
    }

    public static <T> TestBindingOptionsConfigBuilder<T> builder(
        Function<OptionsConfig, T> mapper)
    {
        return new TestBindingOptionsConfigBuilder<>(mapper);
    }

    TestBindingOptionsConfig(
        String mode,
        List<String> catalogs,
        List<Event> events)
    {
        this.mode = mode;
        this.catalogs = catalogs;
        this.events = events;
    }

    public static final class Event
    {
        public final long timestamp;
        public final String message;

        public Event(
            long timestamp,
            String message)
        {
            this.timestamp = timestamp;
            this.message = message;
        }
    }
}
