/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.sse.config;

import static java.util.Collections.emptyList;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public final class SseOptionsConfig extends OptionsConfig
{
    public final int retry;
    public final List<SseRequestConfig> requests;


    public static SseOptionsConfigBuilder<SseOptionsConfig> builder()
    {
        return new SseOptionsConfigBuilder<>(SseOptionsConfig.class::cast);
    }

    public static <T> SseOptionsConfigBuilder<T> builder(
        Function<OptionsConfig, T> mapper)
    {
        return new SseOptionsConfigBuilder<>(mapper);
    }

    SseOptionsConfig(
        int retry,
        List<SseRequestConfig> requests)
    {
        super(resolveModels(requests), List.of());
        this.retry = retry;
        this.requests = requests;
    }

    private static List<ModelConfig> resolveModels(
        List<SseRequestConfig> requests)
    {
        return requests != null && !requests.isEmpty()
            ? requests.stream()
            .flatMap(path ->
                Stream.of(path.content)
                    .filter(Objects::nonNull))
            .collect(Collectors.toList())
            : emptyList();
    }
}
