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
package io.aklivity.zilla.runtime.common.yaml;

public final class YamlConfig
{
    public static final String FEATURE_DIRECTIVES = "io.aklivity.zilla.yaml.feature.directives";
    public static final String FEATURE_DOCUMENT_MARKERS = "io.aklivity.zilla.yaml.feature.document.markers";
    public static final String FEATURE_BLOCK_SCALARS = "io.aklivity.zilla.yaml.feature.block.scalars";
    public static final String FEATURE_FLOW_COLLECTIONS = "io.aklivity.zilla.yaml.feature.flow.collections";
    public static final String FEATURE_ANCHORS = "io.aklivity.zilla.yaml.feature.anchors";
    public static final String FEATURE_ALIASES = "io.aklivity.zilla.yaml.feature.aliases";
    public static final String FEATURE_TAGS = "io.aklivity.zilla.yaml.feature.tags";
    public static final String FEATURE_COMMENTS = "io.aklivity.zilla.yaml.feature.comments";
    public static final String FEATURE_NON_SCALAR_KEYS = "io.aklivity.zilla.yaml.feature.non.scalar.keys";
    public static final String FEATURE_UNIQUE_KEYS = "io.aklivity.zilla.yaml.feature.unique.keys";
    public static final String FEATURE_MULTI_DOCUMENT_STREAMS = "io.aklivity.zilla.yaml.feature.multi.document.streams";
    public static final String SCALAR_RESOLUTION = "io.aklivity.zilla.yaml.scalar.resolution";
    public static final String PRESERVE_SOURCE = "io.aklivity.zilla.yaml.preserve.source";
    public static final String PRESERVE_COMMENTS = "io.aklivity.zilla.yaml.preserve.comments";

    private YamlConfig()
    {
    }
}
