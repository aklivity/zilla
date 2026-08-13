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

import java.util.function.Function;

public class OverlayConfig extends Config
{
    public final String name;
    public final SchemaConfig schema;

    OverlayConfig(
        String name,
        SchemaConfig schema)
    {
        this.name = name;
        this.schema = schema;
    }

    public static <T> OverlayConfigBuilder<T> builder(
        Function<OverlayConfig, T> mapper)
    {
        return new OverlayConfigBuilder<>(mapper);
    }

    public static OverlayConfigBuilder<OverlayConfig> builder()
    {
        return new OverlayConfigBuilder<>(OverlayConfig.class::cast);
    }
}
