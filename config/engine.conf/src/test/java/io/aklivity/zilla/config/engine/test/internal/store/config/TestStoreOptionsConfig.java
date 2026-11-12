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
package io.aklivity.zilla.config.engine.test.internal.store.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.OptionsConfig;

public final class TestStoreOptionsConfig extends OptionsConfig
{
    public final Map<String, String> entries;
    public final List<Assertion> assertions;

    public static TestStoreOptionsConfigBuilder<TestStoreOptionsConfig> builder()
    {
        return new TestStoreOptionsConfigBuilder<>(TestStoreOptionsConfig.class::cast);
    }

    public static <T> TestStoreOptionsConfigBuilder<T> builder(
        Function<OptionsConfig, T> mapper)
    {
        return new TestStoreOptionsConfigBuilder<>(mapper);
    }

    TestStoreOptionsConfig(
        Map<String, String> entries,
        List<Assertion> assertions)
    {
        super(null, null);
        this.entries = entries;
        this.assertions = assertions;
    }

    public static final class Assertion
    {
        public final String op;
        public final String key;
        public final String value;
        public final Duration ttl;

        public Assertion(
            String op,
            String key,
            String value,
            Duration ttl)
        {
            this.op = op;
            this.key = key;
            this.value = value;
            this.ttl = ttl;
        }
    }
}
