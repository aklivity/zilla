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
package io.aklivity.zilla.runtime.classifier.presidio.internal;

import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObjectBuilder;

final class PresidioRequest
{
    private static final JsonBuilderFactory BUILDERS = Json.createBuilderFactory(null);

    static String body(
        String text,
        String language,
        List<String> entities,
        double threshold)
    {
        JsonArrayBuilder names = BUILDERS.createArrayBuilder();
        entities.forEach(names::add);

        JsonObjectBuilder body = BUILDERS.createObjectBuilder()
            .add("text", text)
            .add("language", language)
            .add("entities", names);

        if (threshold > 0.0)
        {
            body.add("score_threshold", threshold);
        }

        return body.build().toString();
    }

    private PresidioRequest()
    {
    }
}
