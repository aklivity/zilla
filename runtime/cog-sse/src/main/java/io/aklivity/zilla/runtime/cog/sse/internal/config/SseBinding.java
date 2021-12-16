/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.runtime.cog.sse.internal.config;

import static java.util.stream.Collectors.toList;

import java.util.List;

import io.aklivity.zilla.runtime.engine.config.Binding;
import io.aklivity.zilla.runtime.engine.config.Role;

public final class SseBinding
{
    private static final SseOptions DEFAULT_OPTIONS = new SseOptions();

    public final long id;
    public final String entry;
    public final SseOptions options;
    public final Role kind;
    public final List<SseRoute> routes;
    public final SseRoute exit;

    public SseBinding(
        Binding binding)
    {
        this.id = binding.id;
        this.entry = binding.entry;
        this.kind = binding.kind;
        this.options = binding.options instanceof SseOptions ? (SseOptions) binding.options : DEFAULT_OPTIONS;
        this.routes = binding.routes.stream().map(SseRoute::new).collect(toList());
        this.exit = binding.exit != null ? new SseRoute(binding.exit) : null;
    }

    public SseRoute resolve(
        long authorization,
        String path)
    {
        return routes.stream()
            .filter(r -> r.when.stream().allMatch(m -> m.matches(path)))
            .findFirst()
            .orElse(exit);
    }
}
