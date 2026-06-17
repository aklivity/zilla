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
package io.aklivity.zilla.runtime.common.yaml.internal;

import java.util.Map;

import io.aklivity.zilla.runtime.common.yaml.YamlConfig;

final class YamlConfiguration
{
    static final YamlConfiguration DEFAULT = new YamlConfiguration(Map.of());

    private final Map<String, ?> config;

    YamlConfiguration(
        Map<String, ?> config)
    {
        this.config = config == null ? Map.of() : Map.copyOf(config);
    }

    Map<String, ?> config()
    {
        return config;
    }

    boolean isDefault()
    {
        return config.isEmpty();
    }

    boolean directives()
    {
        return enabled(YamlConfig.FEATURE_DIRECTIVES, true);
    }

    boolean documentMarkers()
    {
        return enabled(YamlConfig.FEATURE_DOCUMENT_MARKERS, true);
    }

    boolean blockScalars()
    {
        return enabled(YamlConfig.FEATURE_BLOCK_SCALARS, true);
    }

    boolean flowCollections()
    {
        return enabled(YamlConfig.FEATURE_FLOW_COLLECTIONS, true);
    }

    boolean anchors()
    {
        return enabled(YamlConfig.FEATURE_ANCHORS, true);
    }

    boolean aliases()
    {
        return enabled(YamlConfig.FEATURE_ALIASES, true);
    }

    boolean mergeKeys()
    {
        return enabled(YamlConfig.FEATURE_MERGE_KEYS, true);
    }

    boolean tags()
    {
        return enabled(YamlConfig.FEATURE_TAGS, true);
    }

    boolean comments()
    {
        return enabled(YamlConfig.FEATURE_COMMENTS, true);
    }

    boolean nonScalarKeys()
    {
        return enabled(YamlConfig.FEATURE_NON_SCALAR_KEYS, true);
    }

    boolean multiDocumentStreams()
    {
        return enabled(YamlConfig.FEATURE_MULTI_DOCUMENT_STREAMS, true);
    }

    boolean scalarResolution()
    {
        return enabled(YamlConfig.SCALAR_RESOLUTION, true);
    }

    boolean preserveSource()
    {
        return enabled(YamlConfig.PRESERVE_SOURCE, true);
    }

    boolean preserveComments()
    {
        return enabled(YamlConfig.PRESERVE_COMMENTS, true);
    }

    private boolean enabled(
        String name,
        boolean defaultValue)
    {
        Object value = config.get(name);
        return value instanceof Boolean enabled ? enabled : defaultValue;
    }
}
