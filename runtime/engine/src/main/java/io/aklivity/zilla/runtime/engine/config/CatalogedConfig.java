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
package io.aklivity.zilla.runtime.engine.config;

import org.agrona.collections.ObjectHashSet;

import java.util.function.Function;

public class CatalogedConfig
{
    public transient long id;

    public final String name;
    public final ObjectHashSet<SchemaConfig> schemas;

    public CatalogedConfig(
        String name,
        ObjectHashSet<SchemaConfig> schemas)
    {
        this.name = name;
        this.schemas = schemas;
    }

    public static <T> CatalogedConfigBuilder<T> builder(
        Function<CatalogedConfig, T> mapper)
    {
        return new CatalogedConfigBuilder<>(mapper);
    }

    public static CatalogedConfigBuilder<CatalogedConfig> builder()
    {
        return new CatalogedConfigBuilder<>(CatalogedConfig.class::cast);
    }
}
