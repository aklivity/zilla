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
package io.aklivity.zilla.runtime.engine.test.internal.store.config;

import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public final class TestStoreOptionsConfig extends OptionsConfig
{
    public final Map<String, String> entries;

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
        Map<String, String> entries)
    {
        this.entries = entries;
    }
}
