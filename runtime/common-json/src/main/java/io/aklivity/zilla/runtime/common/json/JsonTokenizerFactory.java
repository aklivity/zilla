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

// Selects the JsonTokenizer implementation for this JVM, once: the highest-priority candidate among those that
// report themselves available. A JsonParserImpl instance caches the selected factory rather than calling
// instantiate() per construction, so ServiceLoader lookup (and an implementation's own availability probe)
// happens once per JVM, not once per parser.
public interface JsonTokenizerFactory
{
    // maxValueSize caps the decoded chars retained for one value/key; growth past it fails closed. quoted holds
    // a key back until its separator, whitespace and the value's first byte are in the window, so the position
    // after a key is the start of its (possibly quoted) value token.
    JsonTokenizer create(
        int maxValueSize,
        boolean quoted);

    // Higher wins among available() candidates.
    int priority();

    // Whether this implementation can run in this JVM (e.g. a required CPU/JVM feature is present). Checked
    // once, not per parser.
    boolean available();

    static JsonTokenizerFactory instantiate()
    {
        return instantiate(ServiceLoader.load(JsonTokenizerFactory.class));
    }

    static JsonTokenizerFactory instantiate(
        Iterable<JsonTokenizerFactory> candidates)
    {
        JsonTokenizerFactory selected = null;
        for (JsonTokenizerFactory candidate : candidates)
        {
            if (candidate.available() && (selected == null || candidate.priority() > selected.priority()))
            {
                selected = candidate;
            }
        }
        if (selected == null)
        {
            throw new IllegalStateException("No available JsonTokenizerFactory found");
        }
        return selected;
    }
}
