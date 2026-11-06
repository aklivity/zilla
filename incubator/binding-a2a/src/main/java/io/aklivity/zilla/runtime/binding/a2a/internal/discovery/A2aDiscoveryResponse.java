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
package io.aklivity.zilla.runtime.binding.a2a.internal.discovery;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

import io.aklivity.zilla.runtime.binding.a2a.internal.cache.A2aCachedAgent;

public final class A2aDiscoveryResponse
{
    private static final String MESSAGE_ID_PREFIX = "discovery-";
    private static final String MESSAGE_ID_DEFAULT = "discovery";
    private static final String NO_AGENTS = "No agents found";

    public static byte[] render(
        String requestMessageId,
        List<A2aDiscoveryMatch> matches)
    {
        final JsonArrayBuilder agents = Json.createArrayBuilder();
        matches.forEach(match -> agents.add(agent(match)));

        final String messageId = requestMessageId != null ? MESSAGE_ID_PREFIX + requestMessageId : MESSAGE_ID_DEFAULT;

        return Json.createObjectBuilder()
            .add("message", Json.createObjectBuilder()
                .add("messageId", messageId)
                .add("role", "ROLE_AGENT")
                .add("parts", Json.createArrayBuilder()
                    .add(Json.createObjectBuilder().add("text", summary(matches)))
                    .add(Json.createObjectBuilder().add("data", Json.createObjectBuilder().add("agents", agents)))))
            .build()
            .toString()
            .getBytes(UTF_8);
    }

    private static String summary(
        List<A2aDiscoveryMatch> matches)
    {
        return matches.isEmpty()
            ? NO_AGENTS
            : String.format("Found %d agent%s: %s",
                matches.size(),
                matches.size() == 1 ? "" : "s",
                matches.stream().map(match -> match.agent.agent).collect(Collectors.joining(", ")));
    }

    private static JsonObject agent(
        A2aDiscoveryMatch match)
    {
        final A2aCachedAgent cached = match.agent;

        final JsonObjectBuilder agent = Json.createObjectBuilder().add("agent", cached.agent);

        if (cached.name != null)
        {
            agent.add("name", cached.name);
        }

        if (cached.description != null)
        {
            agent.add("description", cached.description);
        }

        final JsonArrayBuilder skills = Json.createArrayBuilder();
        if (cached.skills != null)
        {
            for (JsonValue skill : cached.skills)
            {
                final JsonObject object = skill.asJsonObject();
                skills.add(Json.createObjectBuilder()
                    .add("id", object.getString("id", ""))
                    .add("name", object.getString("name", "")));
            }
        }
        agent.add("skills", skills);

        final JsonObject primary = primaryInterface(cached.supportedInterfaces);
        if (primary != null)
        {
            agent.add("protocolBinding", primary.getString("protocolBinding", ""));
            agent.add("protocolVersion", primary.getString("protocolVersion", ""));
        }

        if (cached.securitySchemes != null)
        {
            agent.add("securitySchemes", cached.securitySchemes);
        }

        agent.add("score", Math.round(match.score * 100.0) / 100.0);

        return agent.build();
    }

    private static JsonObject primaryInterface(
        JsonArray interfaces)
    {
        return interfaces != null && !interfaces.isEmpty() ? interfaces.getJsonObject(0) : null;
    }

    private A2aDiscoveryResponse()
    {
    }
}
