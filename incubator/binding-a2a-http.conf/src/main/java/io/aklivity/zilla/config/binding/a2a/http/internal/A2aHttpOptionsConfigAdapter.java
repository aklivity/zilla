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
package io.aklivity.zilla.config.binding.a2a.http.internal;

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

import io.aklivity.zilla.config.binding.a2a.http.A2aHttpAuthorizationConfig;
import io.aklivity.zilla.config.binding.a2a.http.A2aHttpCardConfig;
import io.aklivity.zilla.config.binding.a2a.http.A2aHttpOptionsConfig;
import io.aklivity.zilla.config.binding.a2a.http.A2aHttpOptionsConfigBuilder;
import io.aklivity.zilla.config.binding.a2a.http.A2aHttpSkillConfig;
import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.engine.ModelConfigAdapter;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class A2aHttpOptionsConfigAdapter extends ConfigAdapter<OptionsConfig, JsonObject>
{
    private static final String EMBEDDING_NAME = "embedding";
    private static final String STORE_NAME = "store";
    private static final String THRESHOLD_NAME = "threshold";
    private static final String CARD_NAME = "card";
    private static final String NAME_NAME = "name";
    private static final String DESCRIPTION_NAME = "description";
    private static final String VERSION_NAME = "version";
    private static final String DEFAULT_INPUT_MODES_NAME = "defaultInputModes";
    private static final String DEFAULT_OUTPUT_MODES_NAME = "defaultOutputModes";
    private static final String AUTHORIZATION_NAME = "authorization";
    private static final String CREDENTIALS_NAME = "credentials";
    private static final String HEADERS_NAME = "headers";
    private static final String SKILLS_NAME = "skills";
    private static final String EXAMPLES_NAME = "examples";
    private static final String TAGS_NAME = "tags";
    private static final String SCHEMAS_NAME = "schemas";
    private static final String INPUT_NAME = "input";
    private static final String OUTPUT_NAME = "output";

    private final ModelConfigAdapter model = new ModelConfigAdapter();

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        A2aHttpOptionsConfig a2aHttpOptions = (A2aHttpOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        object.add(EMBEDDING_NAME, a2aHttpOptions.embedding);
        object.add(STORE_NAME, a2aHttpOptions.store);
        object.add(THRESHOLD_NAME, a2aHttpOptions.threshold);

        if (a2aHttpOptions.card != null)
        {
            object.add(CARD_NAME, cardObject(a2aHttpOptions.card));
        }

        A2aHttpAuthorizationConfig authorization = a2aHttpOptions.authorization;
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

        if (!a2aHttpOptions.skills.isEmpty())
        {
            JsonObjectBuilder skills = Json.createObjectBuilder();
            for (A2aHttpSkillConfig skill : a2aHttpOptions.skills)
            {
                skills.add(skill.id, skillObject(skill));
            }
            object.add(SKILLS_NAME, skills);
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        A2aHttpOptionsConfigBuilder<A2aHttpOptionsConfig> builder = A2aHttpOptionsConfig.builder()
            .embedding(object.getString(EMBEDDING_NAME))
            .store(object.getString(STORE_NAME));

        if (object.containsKey(THRESHOLD_NAME))
        {
            builder.threshold(object.getJsonNumber(THRESHOLD_NAME).doubleValue());
        }

        if (object.containsKey(CARD_NAME))
        {
            builder.card(card(object.getJsonObject(CARD_NAME)));
        }

        if (object.containsKey(AUTHORIZATION_NAME))
        {
            builder.authorization(authorization(object.getJsonObject(AUTHORIZATION_NAME)));
        }

        if (object.containsKey(SKILLS_NAME))
        {
            JsonObject skills = object.getJsonObject(SKILLS_NAME);
            for (String id : skills.keySet())
            {
                builder.skill(skill(id, skills.getJsonObject(id)));
            }
        }

        return builder.build();
    }

    private JsonObjectBuilder skillObject(
        A2aHttpSkillConfig skill)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();

        if (!skill.id.equals(skill.name))
        {
            object.add(NAME_NAME, skill.name);
        }

        object.add(DESCRIPTION_NAME, skill.description);

        if (!skill.examples.isEmpty())
        {
            object.add(EXAMPLES_NAME, strings(skill.examples));
        }

        if (!skill.tags.isEmpty())
        {
            object.add(TAGS_NAME, strings(skill.tags));
        }

        JsonObjectBuilder schemas = Json.createObjectBuilder();
        schemas.add(INPUT_NAME, model.adaptToJson(skill.input));
        if (skill.output != null)
        {
            schemas.add(OUTPUT_NAME, model.adaptToJson(skill.output));
        }
        object.add(SCHEMAS_NAME, schemas);

        return object;
    }

    private A2aHttpSkillConfig skill(
        String id,
        JsonObject object)
    {
        JsonObject schemas = object.getJsonObject(SCHEMAS_NAME);

        ModelConfig input = model.adaptFromJson(schemas.get(INPUT_NAME));
        ModelConfig output = schemas.containsKey(OUTPUT_NAME)
            ? model.adaptFromJson(schemas.get(OUTPUT_NAME))
            : null;

        return A2aHttpSkillConfig.builder()
            .id(id)
            .name(object.getString(NAME_NAME, null))
            .description(object.getString(DESCRIPTION_NAME))
            .examples(object.containsKey(EXAMPLES_NAME) ? strings(object.getJsonArray(EXAMPLES_NAME)) : null)
            .tags(object.containsKey(TAGS_NAME) ? strings(object.getJsonArray(TAGS_NAME)) : null)
            .input(input)
            .output(output)
            .build();
    }

    private static JsonObjectBuilder cardObject(
        A2aHttpCardConfig card)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();

        if (card.name != null)
        {
            object.add(NAME_NAME, card.name);
        }

        if (card.description != null)
        {
            object.add(DESCRIPTION_NAME, card.description);
        }

        object.add(VERSION_NAME, card.version);
        object.add(DEFAULT_INPUT_MODES_NAME, strings(card.defaultInputModes));
        object.add(DEFAULT_OUTPUT_MODES_NAME, strings(card.defaultOutputModes));

        return object;
    }

    private static A2aHttpCardConfig card(
        JsonObject object)
    {
        return A2aHttpCardConfig.builder()
            .name(object.getString(NAME_NAME, null))
            .description(object.getString(DESCRIPTION_NAME, null))
            .version(object.getString(VERSION_NAME, null))
            .defaultInputModes(object.containsKey(DEFAULT_INPUT_MODES_NAME)
                ? strings(object.getJsonArray(DEFAULT_INPUT_MODES_NAME))
                : null)
            .defaultOutputModes(object.containsKey(DEFAULT_OUTPUT_MODES_NAME)
                ? strings(object.getJsonArray(DEFAULT_OUTPUT_MODES_NAME))
                : null)
            .build();
    }

    private static A2aHttpAuthorizationConfig authorization(
        JsonObject authorizations)
    {
        A2aHttpAuthorizationConfig authorization = null;

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
            authorization = A2aHttpAuthorizationConfig.builder()
                .name(name)
                .headers(headers)
                .build();
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
