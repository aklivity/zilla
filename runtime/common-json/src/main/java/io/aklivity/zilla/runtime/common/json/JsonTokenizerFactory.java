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
package io.aklivity.zilla.runtime.common.json;

import java.util.ServiceLoader;
import java.util.function.Function;

// Selects the JsonTokenizer implementation for this JVM, once: the highest-priority candidate. A JsonParserImpl
// instance caches the selected factory rather than calling instantiate() per construction, so ServiceLoader lookup
// (and an implementation's own probe) happens once per JVM, not once per parser.
public interface JsonTokenizerFactory
{
    // quoted holds a key back until its separator, whitespace and the value's first byte are in the window, so the
    // position after a key is the start of its (possibly quoted) value token.
    JsonTokenizer create(
        boolean quoted);

    // Higher wins. A factory that cannot run in this JVM, or that is not enabled by property, answers a priority
    // below the baseline's, so it is never chosen; property reads the named property, or null when it is not set.
    int priority(
        Function<String, String> property);

    static JsonTokenizerFactory instantiate()
    {
        return instantiate(ServiceLoader.load(JsonTokenizerFactory.class), System::getProperty);
    }

    static JsonTokenizerFactory instantiate(
        Iterable<JsonTokenizerFactory> candidates,
        Function<String, String> property)
    {
        JsonTokenizerFactory selected = null;
        int selectedPriority = Integer.MIN_VALUE;
        for (JsonTokenizerFactory candidate : candidates)
        {
            final int priority = candidate.priority(property);
            if (selected == null || priority > selectedPriority)
            {
                selected = candidate;
                selectedPriority = priority;
            }
        }
        if (selected == null)
        {
            throw new IllegalStateException("No JsonTokenizerFactory found");
        }
        return selected;
    }
}
