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
package io.aklivity.zilla.runtime.common.json;

import java.util.ServiceLoader;

public interface JsonTokenizerFactory
{
    JsonTokenizer create(
        boolean terminalEof,
        int maxValueSize);

    int priority();

    boolean available();

    static JsonTokenizerFactory instantiate()
    {
        return instantiate(ServiceLoader.load(JsonTokenizerFactory.class));
    }

    // Iterable (not ServiceLoader) so tests can pass a fixed list of hand-rolled candidates
    // and exercise the selection logic without any META-INF/services wiring.
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
