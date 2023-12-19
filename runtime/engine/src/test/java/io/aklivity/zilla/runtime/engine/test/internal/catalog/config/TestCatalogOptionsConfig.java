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
package io.aklivity.zilla.runtime.engine.test.internal.catalog.config;

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public class TestCatalogOptionsConfig extends OptionsConfig
{
    public final String schema;
    public final int id;
    public final boolean embed;

    public static TestCatalogOptionsConfigBuilder<TestCatalogOptionsConfig> builder()
    {
        return new TestCatalogOptionsConfigBuilder<>(TestCatalogOptionsConfig.class::cast);
    }

    public static <T> TestCatalogOptionsConfigBuilder<T> builder(
            Function<OptionsConfig, T> mapper)
    {
        return new TestCatalogOptionsConfigBuilder<>(mapper);
    }

    public TestCatalogOptionsConfig(
        int id,
        String schema,
        boolean embed)
    {
        this.schema = schema;
        this.id = id;
        this.embed = embed;
    }
}
