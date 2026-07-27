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

import java.util.List;
import java.util.function.LongFunction;

import io.aklivity.zilla.runtime.common.lang.util.function.LongObjectBiFunction;

public class GuardedConfig
{
    public transient long id;
    public transient LongFunction<String> identity;
    public transient LongObjectBiFunction<String, String> attributes;
    public transient String qname;

    public final String name;
    public final List<String> roles;

    public static GuardedConfigBuilder<GuardedConfig> builder()
    {
        return new GuardedConfigBuilder<>(identity());
    }

    GuardedConfig(
        String name,
        List<String> roles)
    {
        this.name = requireNonNull(name);
        this.roles = requireNonNull(roles);
    }
}
