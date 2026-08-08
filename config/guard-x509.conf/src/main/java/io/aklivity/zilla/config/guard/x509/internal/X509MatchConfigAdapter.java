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
package io.aklivity.zilla.config.guard.x509.internal;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.config.guard.x509.X509MatchConfig;
import io.aklivity.zilla.config.guard.x509.X509MatchConfigBuilder;

public final class X509MatchConfigAdapter implements JsonbAdapter<X509MatchConfig, JsonObject>
{
    @Override
    public JsonObject adaptToJson(
        X509MatchConfig match)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();

        match.fields.forEach(object::add);

        return object.build();
    }

    @Override
    public X509MatchConfig adaptFromJson(
        JsonObject object)
    {
        X509MatchConfigBuilder<X509MatchConfig> match = X509MatchConfig.builder();

        object.forEach((field, pattern) -> match.field(field, ((JsonString) pattern).getString()));

        return match.build();
    }
}
