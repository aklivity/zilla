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

import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

public class AttributeConfig
{
    public transient long id;

    public final String name;
    public final String value;

    public static AttributeConfigBuilder<AttributeConfig> builder()
    {
        return new AttributeConfigBuilder<>(identity());
    }

    AttributeConfig(
        String name,
        String value)
    {
        this.name = requireNonNull(name);
        this.value = requireNonNull(value);
    }
}
