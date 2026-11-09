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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.card;

import java.util.List;
import java.util.Set;

public record AguiA2aExtension(
    String uri,
    boolean required,
    Set<String> features,
    String history)
{
    public static final String URI_V1 = "https://a2a.aklivity.io/ext/agui/v1";
    public static final String URI_PREFIX = "https://a2a.aklivity.io/ext/agui/v";

    public static final String FEATURE_STATE = "state";
    public static final String FEATURE_TOOLS = "tools";
    public static final String FEATURE_HISTORY = "history";
    public static final String HISTORY_CONTEXT = "context";
    public static final String HISTORY_ALWAYS = "always";

    static final List<String> IMPLEMENTED = List.of(URI_V1);
    static final Set<String> FEATURES =
        Set.of("state", "tools", "history", "activity", "reasoning", "steps", "toolCalls", "custom");

    public AguiA2aExtension(
        String uri,
        boolean required,
        Set<String> features)
    {
        this(uri, required, features, HISTORY_CONTEXT);
    }

    public boolean historyAlways()
    {
        return HISTORY_ALWAYS.equals(history);
    }

    public boolean enabled(
        String feature)
    {
        return features.contains(feature);
    }
}
