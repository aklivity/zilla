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
package io.aklivity.zilla.runtime.embedding.glove.internal;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_CACHE_DIRECTORY;

import java.nio.file.Path;

import io.aklivity.zilla.runtime.engine.Configuration;

public class GloveEmbeddingConfiguration extends Configuration
{
    public static final PropertyDef<Path> GLOVE_CACHE_DIRECTORY;

    private static final ConfigurationDef GLOVE_EMBEDDING_CONFIG;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.embedding.glove");
        GLOVE_CACHE_DIRECTORY = config.property(Path.class, "cache.directory",
            GloveEmbeddingConfiguration::cacheDirectory, "glove");
        GLOVE_EMBEDDING_CONFIG = config;
    }

    public GloveEmbeddingConfiguration()
    {
        super(GLOVE_EMBEDDING_CONFIG, new Configuration());
    }

    public GloveEmbeddingConfiguration(
        Configuration config)
    {
        super(GLOVE_EMBEDDING_CONFIG, config);
    }

    public Path cacheDirectory()
    {
        return GLOVE_CACHE_DIRECTORY.get(this);
    }

    private static Path cacheDirectory(
        Configuration config,
        String cacheDirectory)
    {
        return ENGINE_CACHE_DIRECTORY.get(config).resolve(cacheDirectory);
    }
}
