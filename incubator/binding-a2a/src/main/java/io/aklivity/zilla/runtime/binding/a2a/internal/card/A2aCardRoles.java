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
package io.aklivity.zilla.runtime.binding.a2a.internal.card;

import java.util.List;
import java.util.function.Function;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

public final class A2aCardRoles
{
    private static final String SECURITY_REQUIREMENTS = "securityRequirements";
    private static final String SECURITY_SCHEME = "oauth2";

    public static JsonObject inject(
        JsonObject canonical,
        Function<String, List<String>> rolesOf)
    {
        final JsonObjectBuilder builder = Json.createObjectBuilder();
        canonical.forEach((name, value) -> builder.add(name,
            "skills".equals(name) && value instanceof JsonArray skills ? skills(skills, rolesOf) : value));
        return builder.build();
    }

    private static JsonArray skills(
        JsonArray skills,
        Function<String, List<String>> rolesOf)
    {
        final JsonArrayBuilder builder = Json.createArrayBuilder();
        skills.forEach(item -> builder.add(item instanceof JsonObject skill ? skill(skill, rolesOf) : item));
        return builder.build();
    }

    private static JsonObject skill(
        JsonObject skill,
        Function<String, List<String>> rolesOf)
    {
        final JsonValue id = skill.get("id");
        final List<String> roles = id instanceof JsonString name ? rolesOf.apply(name.getString()) : List.of();

        JsonObject result = skill;

        if (!roles.isEmpty())
        {
            final JsonObjectBuilder builder = Json.createObjectBuilder();
            final JsonValue declared = skill.get(SECURITY_REQUIREMENTS);

            skill.forEach((name, value) -> builder.add(name,
                SECURITY_REQUIREMENTS.equals(name) && value instanceof JsonArray requirements
                    ? Json.createArrayBuilder(requirements).add(requirement(roles)).build()
                    : value));

            if (!(declared instanceof JsonArray))
            {
                builder.add(SECURITY_REQUIREMENTS, Json.createArrayBuilder().add(requirement(roles)));
            }

            result = builder.build();
        }

        return result;
    }

    private static JsonObject requirement(
        List<String> roles)
    {
        final JsonArrayBuilder list = Json.createArrayBuilder();
        roles.forEach(list::add);

        return Json.createObjectBuilder()
            .add("schemes", Json.createObjectBuilder()
                .add(SECURITY_SCHEME, Json.createObjectBuilder()
                    .add("list", list)))
            .build();
    }

    private A2aCardRoles()
    {
    }
}
