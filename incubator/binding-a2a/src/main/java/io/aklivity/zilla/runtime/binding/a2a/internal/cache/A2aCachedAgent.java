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
package io.aklivity.zilla.runtime.binding.a2a.internal.cache;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

public final class A2aCachedAgent
{
    public final String agent;
    public final String name;
    public final String description;
    public final JsonArray skills;
    public final JsonArray supportedInterfaces;
    public final JsonObject securitySchemes;

    A2aCachedAgent(
        String agent,
        JsonObject card)
    {
        this.agent = agent;
        this.name = card.getString("name", null);
        this.description = card.getString("description", null);
        this.skills = card.getJsonArray("skills");
        this.supportedInterfaces = card.getJsonArray("supportedInterfaces");
        this.securitySchemes = card.getJsonObject("securitySchemes");
    }
}
