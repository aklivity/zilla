/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.http.filesystem.internal.config;

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.WithConfig;

public final class HttpFileSystemWithConfig extends WithConfig
{
    public final String directory;
    public final String path;

    HttpFileSystemWithConfig(
        String directory,
        String path)
    {
        this.directory = directory;
        this.path = path;
    }

    public static HttpFileSystemWithConfigBuilder<HttpFileSystemWithConfig> builder()
    {
        return new HttpFileSystemWithConfigBuilder<>(HttpFileSystemWithConfig.class::cast);
    }

    public static <T> HttpFileSystemWithConfigBuilder<T> builder(
        Function<WithConfig, T> mapper)
    {
        return new HttpFileSystemWithConfigBuilder<>(mapper);
    }
}
