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
package io.aklivity.zilla.runtime.binding.sse.internal.config;

import static io.aklivity.zilla.runtime.binding.sse.internal.config.SseOptionsConfigAdapter.RETRY_DEFAULT;
import static java.util.stream.Collectors.toList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.binding.sse.config.SseOptionsConfig;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;

public final class SseBindingConfig
{
    private static final SseOptionsConfig DEFAULT_OPTIONS = SseOptionsConfig.builder().retry(RETRY_DEFAULT).build();

    public final long id;
    public final String name;
    public final SseOptionsConfig options;
    public final KindConfig kind;
    public final List<SseRouteConfig> routes;
    public final Map<Matcher, ModelConfig> requests;

    public SseBindingConfig(
        BindingConfig binding)
    {
        this.id = binding.id;
        this.name = binding.name;
        this.kind = binding.kind;
        this.options = binding.options instanceof SseOptionsConfig ? (SseOptionsConfig) binding.options : DEFAULT_OPTIONS;
        this.routes = binding.routes.stream().map(SseRouteConfig::new).collect(toList());
        this.requests = new HashMap<>();
        if (options.requests != null)
        {
            options.requests.forEach(p ->
                requests.put(Pattern.compile(p.path).matcher(""), p.content));
        }
    }

    public SseRouteConfig resolve(
        long authorization,
        String path)
    {
        return routes.stream()
            .filter(r -> r.authorized(authorization, path) && r.matches(path))
            .findFirst()
            .orElse(null);
    }

    public ModelConfig supplyModelConfig(
        String path)
    {
        ModelConfig config = null;
        if (requests != null)
        {
            for (Map.Entry<Matcher, ModelConfig> e : requests.entrySet())
            {
                final Matcher matcher = e.getKey();
                matcher.reset(path);
                if (matcher.find())
                {
                    config = e.getValue();
                    break;
                }
            }
        }
        return config;
    }
}
