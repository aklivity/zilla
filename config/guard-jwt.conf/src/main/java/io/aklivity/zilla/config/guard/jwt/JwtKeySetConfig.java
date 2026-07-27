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
package io.aklivity.zilla.config.guard.jwt;

import java.util.List;
import java.util.function.Function;

public class JwtKeySetConfig
{
    public final List<JwtKeyConfig> keys;

    public static JwtKeySetConfigBuilder<JwtKeySetConfig> builder()
    {
        return new JwtKeySetConfigBuilder<>(JwtKeySetConfig.class::cast);
    }

    public static <T> JwtKeySetConfigBuilder<T> builder(
        Function<JwtKeySetConfig, T> mapper)
    {
        return new JwtKeySetConfigBuilder<>(mapper);
    }

    JwtKeySetConfig(
        List<JwtKeyConfig> keys)
    {
        this.keys = keys;
    }
}
