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
package io.aklivity.zilla.runtime.engine.config;

import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

public class MetricRefConfig
{
    public final String name;

    public static MetricRefConfigBuilder<MetricRefConfig> builder()
    {
        return new MetricRefConfigBuilder<>(identity());
    }

    MetricRefConfig(
        String name)
    {
        this.name = requireNonNull(name);
    }
}
