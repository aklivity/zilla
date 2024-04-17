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

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public class FilesystemOptionsConfig extends OptionsConfig
{
    public final List<FilesystemSchemaConfig> subjects;
    public final Function<String, String> readURL;

    public static FilesystemOptionsConfigBuilder<FilesystemOptionsConfig> builder()
    {
        return new FilesystemOptionsConfigBuilder<>(FilesystemOptionsConfig.class::cast);
    }

    public static <T> FilesystemOptionsConfigBuilder<T> builder(
            Function<OptionsConfig, T> mapper)
    {
        return new FilesystemOptionsConfigBuilder<>(mapper);
    }

    public FilesystemOptionsConfig(
        List<FilesystemSchemaConfig> subjects,
        Function<String, String> readURL)
    {
        this.subjects = subjects;
        this.readURL = readURL;
    }
}
