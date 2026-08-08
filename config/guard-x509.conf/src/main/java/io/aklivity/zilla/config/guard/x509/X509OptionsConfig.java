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

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.OptionsConfig;

public class X509OptionsConfig extends OptionsConfig
{
    public final String identity;
    public final Map<String, String> attributes;
    public final Map<String, List<X509MatchConfig>> roles;

    public static X509OptionsConfigBuilder<X509OptionsConfig> builder()
    {
        return new X509OptionsConfigBuilder<>(X509OptionsConfig.class::cast);
    }

    public static <T> X509OptionsConfigBuilder<T> builder(
        Function<OptionsConfig, T> mapper)
    {
        return new X509OptionsConfigBuilder<>(mapper);
    }

    X509OptionsConfig(
        String identity,
        Map<String, String> attributes,
        Map<String, List<X509MatchConfig>> roles)
    {
        this.identity = identity;
        this.attributes = attributes;
        this.roles = roles;
    }
}
