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
package io.aklivity.zilla.config.engine;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class OptionsConfig extends Config.Extensible
{
    public final List<String> resources;

    public OptionsConfig()
    {
        this(Collections.emptyList());
    }

    public OptionsConfig(
        List<String> resources)
    {
        this(resources, null, null);
    }

    public OptionsConfig(
        List<String> resources,
        Map<String, Config> extensions,
        List<NamedConfig> refs)
    {
        super(extensions, refs);
        this.resources = resources;
    }
}
