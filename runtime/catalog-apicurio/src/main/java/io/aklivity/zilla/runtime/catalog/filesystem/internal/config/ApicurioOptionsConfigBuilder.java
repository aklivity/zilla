/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.catalog.filesystem.internal.config;

import java.time.Duration;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public final class ApicurioOptionsConfigBuilder<T> extends ConfigBuilder<T, ApicurioOptionsConfigBuilder<T>>
{
    public static final String GLOBAL_ID = "globalId";
    public static final String DEFAULT_ID_ENCODING = "default";
    public static final String LEGACY_ID_ENCODING = "legacy";
    public static final String CONTENT_ID = "contentId";
    private static final Duration MAX_AGE_DEFAULT = Duration.ofSeconds(300);

    private final Function<OptionsConfig, T> mapper;

    private String url;
    private String groupId;
    private String useId = GLOBAL_ID;
    private String idEncoding = DEFAULT_ID_ENCODING;
    private Duration maxAge;

    ApicurioOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<ApicurioOptionsConfigBuilder<T>> thisType()
    {
        return (Class<ApicurioOptionsConfigBuilder<T>>) getClass();
    }

    public ApicurioOptionsConfigBuilder<T> url(
        String url)
    {
        this.url = url;
        return this;
    }

    public ApicurioOptionsConfigBuilder<T> groupId(
        String groupId)
    {
        this.groupId = groupId;
        return this;
    }

    public ApicurioOptionsConfigBuilder<T> useId(
        String useId)
    {
        this.useId = useId;
        return this;
    }

    public ApicurioOptionsConfigBuilder<T> idEncoding(
        String idEncoding)
    {
        this.idEncoding = idEncoding;
        return this;
    }

    public ApicurioOptionsConfigBuilder<T> maxAge(
        Duration maxAge)
    {
        this.maxAge = maxAge;
        return this;
    }

    @Override
    public T build()
    {
        Duration maxAge = (this.maxAge != null) ? this.maxAge : MAX_AGE_DEFAULT;
        return mapper.apply(new ApicurioOptionsConfig(url, groupId, useId, idEncoding, maxAge));
    }
}
