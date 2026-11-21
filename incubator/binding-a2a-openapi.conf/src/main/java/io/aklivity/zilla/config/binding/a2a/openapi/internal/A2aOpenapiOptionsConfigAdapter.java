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
package io.aklivity.zilla.config.binding.a2a.openapi.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import io.aklivity.zilla.config.binding.a2a.openapi.A2aOpenapiAgentConfig;
import io.aklivity.zilla.config.binding.a2a.openapi.A2aOpenapiAuthorizationConfig;
import io.aklivity.zilla.config.binding.a2a.openapi.A2aOpenapiCatalogConfig;
import io.aklivity.zilla.config.binding.a2a.openapi.A2aOpenapiCatalogConfigBuilder;
import io.aklivity.zilla.config.binding.a2a.openapi.A2aOpenapiOptionsConfig;
import io.aklivity.zilla.config.binding.a2a.openapi.A2aOpenapiOptionsConfigBuilder;
import io.aklivity.zilla.config.binding.a2a.openapi.A2aOpenapiSkillConfig;
import io.aklivity.zilla.config.binding.a2a.openapi.A2aOpenapiSpecificationConfig;
import io.aklivity.zilla.config.binding.a2a.openapi.A2aOpenapiSpecificationConfigBuilder;
import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.engine.ModelConfigAdapter;
import io.aklivity.zilla.config.engine.OptionsConfig;
import io.aklivity.zilla.config.engine.OverlayConfigAdapter;

public final class A2aOpenapiOptionsConfigAdapter extends ConfigAdapter<OptionsConfig, JsonObject>
{
    private static final String AGENT_NAME = "agent";
    private static final String NAME_NAME = "name";
    private static final String DESCRIPTION_NAME = "description";
    private static final String VERSION_NAME = "version";
    private static final String AUTHORIZATION_NAME = "authorization";
    private static final String CREDENTIALS_NAME = "credentials";
    private static final String HEADERS_NAME = "headers";
    private static final String SPECS_NAME = "specs";
    private static final String SECURITY_NAME = "security";
    private static final String OVERLAY_NAME = "overlay";
    private static final String SERVER_NAME = "server";
    private static final String CATALOG_NAME = "catalog";
    private static final String SUBJECT_NAME = "subject";
    private static final String SKILLS_NAME = "skills";
    private static final String EXAMPLES_NAME = "examples";
    private static final String TAGS_NAME = "tags";
    private static final String INPUT_NAME = "input";
    private static final String OUTPUT_NAME = "output";
    private static final String EMBEDDING_NAME = "embedding";
    private static final String STORE_NAME = "store";
    private static final String THRESHOLD_NAME = "threshold";

    private final ModelConfigAdapter model = new ModelConfigAdapter();
    private final OverlayConfigAdapter overlay = new OverlayConfigAdapter();

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        A2aOpenapiOptionsConfig a2aOpenapiOptions = (A2aOpenapiOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (a2aOpenapiOptions.agent != null)
        {
            object.add(AGENT_NAME, agentObject(a2aOpenapiOptions.agent));
        }

        A2aOpenapiAuthorizationConfig authorization = a2aOpenapiOptions.authorization;
        if (authorization != null)
        {
            JsonObjectBuilder headers = Json.createObjectBuilder();
            if (authorization.headers != null)
            {
                authorization.headers.forEach(headers::add);
            }
            JsonObjectBuilder credentials = Json.createObjectBuilder();
            credentials.add(HEADERS_NAME, headers);
            JsonObjectBuilder guard = Json.createObjectBuilder();
            guard.add(CREDENTIALS_NAME, credentials);
            JsonObjectBuilder authorizations = Json.createObjectBuilder();
            authorizations.add(authorization.name, guard);
            object.add(AUTHORIZATION_NAME, authorizations);
        }

        if (a2aOpenapiOptions.specs != null)
        {
            final JsonObjectBuilder specs = Json.createObjectBuilder();
            for (A2aOpenapiSpecificationConfig spec : a2aOpenapiOptions.specs)
            {
                specs.add(spec.label, specObject(spec));
            }
            object.add(SPECS_NAME, specs);
        }

        if (!a2aOpenapiOptions.skills.isEmpty())
        {
            JsonObjectBuilder skills = Json.createObjectBuilder();
            for (A2aOpenapiSkillConfig skill : a2aOpenapiOptions.skills)
            {
                skills.add(skill.id, skillObject(skill));
            }
            object.add(SKILLS_NAME, skills);
        }

        object.add(EMBEDDING_NAME, a2aOpenapiOptions.embedding);
        object.add(STORE_NAME, a2aOpenapiOptions.store);
        object.add(THRESHOLD_NAME, a2aOpenapiOptions.threshold);

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        A2aOpenapiOptionsConfigBuilder<A2aOpenapiOptionsConfig> a2aOpenapiOptions = A2aOpenapiOptionsConfig.builder()
            .embedding(object.getString(EMBEDDING_NAME, null))
            .store(object.getString(STORE_NAME, null));

        if (object.containsKey(THRESHOLD_NAME))
        {
            a2aOpenapiOptions.threshold(object.getJsonNumber(THRESHOLD_NAME).doubleValue());
        }

        if (object.containsKey(AGENT_NAME))
        {
            a2aOpenapiOptions.agent(agent(object.getJsonObject(AGENT_NAME)));
        }

        if (object.containsKey(AUTHORIZATION_NAME))
        {
            a2aOpenapiOptions.authorization(authorization(object.getJsonObject(AUTHORIZATION_NAME)));
        }

        if (object.containsKey(SPECS_NAME))
        {
            JsonObject specs = object.getJsonObject(SPECS_NAME);
            for (Map.Entry<String, JsonValue> entry : specs.entrySet())
            {
                a2aOpenapiOptions.spec(spec(entry.getKey(), entry.getValue().asJsonObject()));
            }
        }

        if (object.containsKey(SKILLS_NAME))
        {
            JsonObject skills = object.getJsonObject(SKILLS_NAME);
            for (String id : skills.keySet())
            {
                a2aOpenapiOptions.skill(skill(id, skills.getJsonObject(id)));
            }
        }

        return a2aOpenapiOptions.build();
    }

    private JsonObjectBuilder specObject(
        A2aOpenapiSpecificationConfig spec)
    {
        final JsonObjectBuilder specObject = Json.createObjectBuilder();
        final JsonObjectBuilder catalogObject = Json.createObjectBuilder();
        for (A2aOpenapiCatalogConfig catalog : spec.catalogs)
        {
            JsonObjectBuilder schemaObject = Json.createObjectBuilder();
            schemaObject.add(SUBJECT_NAME, catalog.subject);

            if (catalog.version != null)
            {
                schemaObject.add(VERSION_NAME, catalog.version);
            }

            if (catalog.overlay != null)
            {
                schemaObject.add(OVERLAY_NAME, overlay.adaptToJson(catalog.overlay));
            }

            catalogObject.add(catalog.name, schemaObject);
        }
        specObject.add(CATALOG_NAME, catalogObject);

        if (spec.server != null)
        {
            specObject.add(SERVER_NAME, spec.server);
        }

        if (spec.security != null && !spec.security.isEmpty())
        {
            final JsonObjectBuilder security = Json.createObjectBuilder();
            spec.security.forEach(security::add);
            specObject.add(SECURITY_NAME, security);
        }

        return specObject;
    }

    private A2aOpenapiSpecificationConfig spec(
        String label,
        JsonObject specObject)
    {
        A2aOpenapiSpecificationConfigBuilder<A2aOpenapiSpecificationConfig> spec = A2aOpenapiSpecificationConfig.builder()
            .label(label)
            .server(specObject.getString(SERVER_NAME, null));

        if (specObject.containsKey(CATALOG_NAME))
        {
            final JsonObject catalog = specObject.getJsonObject(CATALOG_NAME);
            for (Map.Entry<String, JsonValue> catalogEntry : catalog.entrySet())
            {
                final JsonObject catalogObject = catalogEntry.getValue().asJsonObject();

                final A2aOpenapiCatalogConfigBuilder<A2aOpenapiCatalogConfig> catalogBuilder =
                    A2aOpenapiCatalogConfig.builder()
                        .name(catalogEntry.getKey())
                        .subject(catalogObject.getString(SUBJECT_NAME, null))
                        .version(catalogObject.getString(VERSION_NAME, null));

                if (catalogObject.containsKey(OVERLAY_NAME))
                {
                    catalogBuilder.overlay(overlay.adaptFromJson(catalogObject.getJsonObject(OVERLAY_NAME)));
                }

                spec.catalog(catalogBuilder.build());
            }
        }

        if (specObject.containsKey(SECURITY_NAME))
        {
            Map<String, String> security = new LinkedHashMap<>();
            final JsonObject securityObject = specObject.getJsonObject(SECURITY_NAME);
            for (String scheme : securityObject.keySet())
            {
                security.put(scheme, securityObject.getString(scheme));
            }
            spec.security(security);
        }

        return spec.build();
    }

    private JsonObjectBuilder skillObject(
        A2aOpenapiSkillConfig skill)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();

        if (skill.name != null)
        {
            object.add(NAME_NAME, skill.name);
        }

        if (skill.description != null)
        {
            object.add(DESCRIPTION_NAME, skill.description);
        }

        if (skill.examples != null)
        {
            object.add(EXAMPLES_NAME, strings(skill.examples));
        }

        if (skill.tags != null)
        {
            object.add(TAGS_NAME, strings(skill.tags));
        }

        if (skill.input != null)
        {
            model.adaptType(skill.input.model);
            object.add(INPUT_NAME, model.adaptToJson(skill.input));
        }

        if (skill.output != null)
        {
            model.adaptType(skill.output.model);
            object.add(OUTPUT_NAME, model.adaptToJson(skill.output));
        }

        return object;
    }

    private A2aOpenapiSkillConfig skill(
        String id,
        JsonObject object)
    {
        ModelConfig input = object.containsKey(INPUT_NAME)
            ? model.adaptFromJson(object.get(INPUT_NAME))
            : null;
        ModelConfig output = object.containsKey(OUTPUT_NAME)
            ? model.adaptFromJson(object.get(OUTPUT_NAME))
            : null;

        return A2aOpenapiSkillConfig.builder()
            .id(id)
            .name(object.getString(NAME_NAME, null))
            .description(object.getString(DESCRIPTION_NAME, null))
            .examples(object.containsKey(EXAMPLES_NAME) ? strings(object.getJsonArray(EXAMPLES_NAME)) : null)
            .tags(object.containsKey(TAGS_NAME) ? strings(object.getJsonArray(TAGS_NAME)) : null)
            .input(input)
            .output(output)
            .build();
    }

    private static JsonObjectBuilder agentObject(
        A2aOpenapiAgentConfig agent)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();

        if (agent.name != null)
        {
            object.add(NAME_NAME, agent.name);
        }

        if (agent.description != null)
        {
            object.add(DESCRIPTION_NAME, agent.description);
        }

        if (agent.version != null)
        {
            object.add(VERSION_NAME, agent.version);
        }

        return object;
    }

    private static A2aOpenapiAgentConfig agent(
        JsonObject object)
    {
        return A2aOpenapiAgentConfig.builder()
            .name(object.getString(NAME_NAME, null))
            .description(object.getString(DESCRIPTION_NAME, null))
            .version(object.getString(VERSION_NAME, null))
            .build();
    }

    private static A2aOpenapiAuthorizationConfig authorization(
        JsonObject authorizations)
    {
        A2aOpenapiAuthorizationConfig authorization = null;

        for (String name : authorizations.keySet())
        {
            Map<String, String> headers = new LinkedHashMap<>();
            JsonObject guard = authorizations.getJsonObject(name);
            if (guard.containsKey(CREDENTIALS_NAME))
            {
                JsonObject credentials = guard.getJsonObject(CREDENTIALS_NAME);
                if (credentials.containsKey(HEADERS_NAME))
                {
                    JsonObject headersObject = credentials.getJsonObject(HEADERS_NAME);
                    for (String header : headersObject.keySet())
                    {
                        headers.put(header, headersObject.getString(header));
                    }
                }
            }
            authorization = new A2aOpenapiAuthorizationConfig(name, headers);
        }

        return authorization;
    }

    private static JsonArrayBuilder strings(
        List<String> values)
    {
        JsonArrayBuilder array = Json.createArrayBuilder();
        values.forEach(array::add);
        return array;
    }

    private static List<String> strings(
        JsonArray array)
    {
        List<String> values = new ArrayList<>();
        array.getValuesAs(JsonString.class).forEach(value -> values.add(value.getString()));
        return values;
    }
}
