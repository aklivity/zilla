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
package io.aklivity.zilla.config.guard.x509;

import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.Config;

public final class X509MatchConfig extends Config
{
    public final Map<String, String> fields;

    public static X509MatchConfigBuilder<X509MatchConfig> builder()
    {
        return new X509MatchConfigBuilder<>(Function.identity());
    }

    public static <T> X509MatchConfigBuilder<T> builder(
        Function<X509MatchConfig, T> mapper)
    {
        return new X509MatchConfigBuilder<>(mapper);
    }

    X509MatchConfig(
        Map<String, String> fields)
    {
        this.fields = fields;
    }
}
