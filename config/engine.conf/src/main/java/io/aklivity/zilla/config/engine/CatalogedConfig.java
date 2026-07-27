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
package io.aklivity.zilla.config.engine;

import java.util.List;
import java.util.function.Function;

public class CatalogedConfig
{
    public transient long id;

    public final String name;
    public final List<SchemaConfig> schemas;

    CatalogedConfig(
        String name,
        List<SchemaConfig> schemas)
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
