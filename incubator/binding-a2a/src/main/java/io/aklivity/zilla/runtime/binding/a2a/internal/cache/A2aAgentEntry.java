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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import io.aklivity.zilla.runtime.binding.a2a.internal.config.A2aProxyRouteConfig;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;

public final class A2aAgentEntry
{
    private static final String SKILLS = "skills";
    private static final String SECURITY_REQUIREMENTS = "securityRequirements";
    private static final String SCHEMES = "schemes";
    private static final String LIST = "list";

    public final long bindingId;
    public final String agent;
    public final A2aProxyRouteConfig route;

    final String key;
    final String lockKey;
    final List<Runnable> waiters = new ArrayList<>();

    A2aAgentCache.Hydration hydration;
    long attemptedAt;
    byte[] failure;

    private String card;
    private byte[] cardBytes;
    private JsonObject cardJson;
    private List<List<List<String>>> skillGates = List.of();

    A2aAgentEntry(
        long bindingId,
        String agent,
        A2aProxyRouteConfig route)
    {
        this.bindingId = bindingId;
        this.agent = agent;
        this.route = route;
        this.key = "a2a/%d/%s".formatted(bindingId, agent);
        this.lockKey = key + ".lock";
    }

    public String card()
    {
        return card;
    }

    public byte[] failure()
    {
        return failure;
    }

    void adopt(
        String card)
    {
        JsonObject parsed = null;
        try (JsonReader reader = Json.createReader(new StringReader(card)))
        {
            parsed = reader.readObject();
        }
        catch (JsonException | ClassCastException ex)
        {
            parsed = null;
        }

        if (parsed != null)
        {
            this.card = card;
            this.cardBytes = card.getBytes(UTF_8);
            this.cardJson = parsed;
            this.skillGates = gatesOf(parsed.get(SKILLS) instanceof JsonArray skills ? skills : JsonArray.EMPTY_JSON_ARRAY);
            this.failure = null;
        }
    }

    public byte[] render(
        long authorization,
        GuardHandler guard)
    {
        final JsonObject visible = visibleCard(authorization, guard);

        return visible == null
            ? null
            : visible == cardJson
                ? cardBytes
                : visible.toString().getBytes(UTF_8);
    }

    public List<String> requiredRoles()
    {
        return skillGates.stream()
            .flatMap(List::stream)
            .flatMap(List::stream)
            .distinct()
            .collect(toList());
    }

    JsonObject visibleCard(
        long authorization,
        GuardHandler guard)
    {
        final JsonArray skills = cardJson.get(SKILLS) instanceof JsonArray array ? array : JsonArray.EMPTY_JSON_ARRAY;
        final List<JsonValue> visible = visibleSkills(skills, authorization, guard);

        return visible.size() == skills.size()
            ? cardJson
            : visible.isEmpty()
                ? null
                : withSkills(visible);
    }

    private List<JsonValue> visibleSkills(
        JsonArray skills,
        long authorization,
        GuardHandler guard)
    {
        final List<JsonValue> visible = new ArrayList<>();

        for (int index = 0; index < skills.size(); index++)
        {
            final List<List<String>> alternatives = skillGates.get(index);
            if (alternatives.isEmpty() || guard == null ||
                alternatives.stream().anyMatch(roles -> guard.verify(authorization, roles)))
            {
                visible.add(skills.get(index));
            }
        }

        return visible;
    }

    private JsonObject withSkills(
        List<JsonValue> visible)
    {
        final JsonArrayBuilder skills = Json.createArrayBuilder();
        visible.forEach(skills::add);

        final JsonObjectBuilder builder = Json.createObjectBuilder();
        cardJson.forEach((name, value) -> builder.add(name, SKILLS.equals(name) ? skills.build() : value));

        return builder.build();
    }

    private static List<List<List<String>>> gatesOf(
        JsonArray skills)
    {
        final List<List<List<String>>> gates = new ArrayList<>();

        for (JsonValue skill : skills)
        {
            final List<List<String>> alternatives = new ArrayList<>();

            if (skill instanceof JsonObject object && object.get(SECURITY_REQUIREMENTS) instanceof JsonArray requirements)
            {
                for (JsonValue requirement : requirements)
                {
                    alternatives.add(rolesOf(requirement));
                }
            }

            gates.add(alternatives.stream().anyMatch(List::isEmpty) ? List.of() : alternatives);
        }

        return gates;
    }

    private static List<String> rolesOf(
        JsonValue requirement)
    {
        final List<String> roles = new ArrayList<>();

        if (requirement instanceof JsonObject object && object.get(SCHEMES) instanceof JsonObject schemes)
        {
            for (JsonValue scheme : schemes.values())
            {
                if (scheme instanceof JsonObject object2 && object2.get(LIST) instanceof JsonArray list)
                {
                    list.forEach(role -> roles.add(((JsonString) role).getString()));
                }
            }
        }

        return roles;
    }
}
