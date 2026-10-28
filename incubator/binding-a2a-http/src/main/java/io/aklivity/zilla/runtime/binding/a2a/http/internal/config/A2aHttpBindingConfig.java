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

package io.aklivity.zilla.runtime.binding.a2a.http.internal.config;

import static java.util.stream.Collectors.toList;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToLongFunction;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;

import io.aklivity.zilla.config.binding.a2a.http.A2aHttpAuthorizationConfig;
import io.aklivity.zilla.config.binding.a2a.http.A2aHttpCardConfig;
import io.aklivity.zilla.config.binding.a2a.http.A2aHttpOptionsConfig;
import io.aklivity.zilla.config.binding.a2a.http.A2aHttpSkillConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.CatalogedConfig;
import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.engine.SchemaConfig;
import io.aklivity.zilla.runtime.binding.a2a.http.internal.A2aHttpConfiguration;
import io.aklivity.zilla.runtime.binding.a2a.http.internal.skill.A2aHttpSkillVectors;
import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.common.vector.Vectors;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.embedding.EmbeddingHandler;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;

public final class A2aHttpBindingConfig
{
    private static final String CREDENTIALS_TOKEN = "{credentials}";
    private static final String IDENTITY_TOKEN = "{identity}";
    private static final String DEFAULT_CARD_VERSION = "1.0.0";
    private static final List<String> DEFAULT_INPUT_MODES = List.of("text/plain", "application/json");
    private static final List<String> DEFAULT_OUTPUT_MODES = List.of("application/json", "text/plain");

    public final long id;
    public final A2aHttpOptionsConfig options;
    public final List<A2aHttpRouteConfig> routes;
    public final GuardHandler guard;
    public final EmbeddingHandler embedding;
    public final A2aHttpSkillVectors vectors;

    private final String name;
    private final EngineContext context;
    private final ToLongFunction<String> resolveId;
    private final Map<String, A2aHttpSkillConfig> skillsById;
    private final List<A2aHttpSkillConfig> phraseSkills;
    private final Map<ModelConfig, JsonSchema> jsonSchemas;
    private final Map<A2aHttpRouteConfig, List<String>> unsatisfiedAccessors;
    private final Function<ModelConfig, JsonSchema> resolveJsonSchemaFn = this::resolveJsonSchema;

    private byte[] cardJson;
    private String skillsSummary;

    public A2aHttpBindingConfig(
        BindingConfig binding,
        EngineContext context,
        A2aHttpConfiguration config)
    {
        this.id = binding.id;
        this.name = binding.name;
        this.context = context;
        this.resolveId = binding.resolveId;
        this.options = (A2aHttpOptionsConfig) binding.options;

        this.routes = binding.routes.stream()
            .map(A2aHttpRouteConfig::new)
            .collect(toList());

        this.skillsById = new LinkedHashMap<>();
        this.options.skills.forEach(skill -> skillsById.putIfAbsent(skill.id, skill));

        final List<String> phrases = new ArrayList<>();
        final List<A2aHttpSkillConfig> phraseSkills = new ArrayList<>();
        for (A2aHttpSkillConfig skill : skillsById.values())
        {
            phrases.add(skill.description);
            phraseSkills.add(skill);
            if (skill.examples != null)
            {
                for (String example : skill.examples)
                {
                    phrases.add(example);
                    phraseSkills.add(skill);
                }
            }
        }
        this.phraseSkills = phraseSkills;

        GuardHandler guard = null;
        if (options.authorization != null)
        {
            final long guardId = resolveId.applyAsLong(options.authorization.name);
            guard = context.supplyGuard(guardId);
        }
        this.guard = guard;

        this.embedding = context.supplyEmbedding(resolveId.applyAsLong(options.embedding));
        this.vectors = new A2aHttpSkillVectors(
            embedding,
            context.supplyStore(resolveId.applyAsLong(options.store)),
            context.signaler(),
            phrases,
            config.lockTtl(),
            config.timeout());

        this.jsonSchemas = new IdentityHashMap<>();
        this.unsatisfiedAccessors = new IdentityHashMap<>();
    }

    public A2aHttpSkillConfig skill(
        String id)
    {
        return skillsById.get(id);
    }

    public Collection<A2aHttpSkillConfig> skills()
    {
        return skillsById.values();
    }

    public A2aHttpRouteConfig resolveRoute(
        String skillId)
    {
        A2aHttpRouteConfig mapping = null;
        for (A2aHttpRouteConfig route : routes)
        {
            if (mapping == null && route.with != null && route.skill != null && route.skill.equals(skillId))
            {
                mapping = route;
            }
        }
        return mapping;
    }

    public boolean authorized(
        String skillId,
        long authorization)
    {
        boolean authorized = true;
        for (A2aHttpRouteConfig route : routes)
        {
            if (route.appliesToSkill(skillId))
            {
                authorized &= route.authorized(authorization);
            }
        }
        return authorized;
    }

    public String scopes(
        String skillId)
    {
        final List<String> roles = new ArrayList<>();
        for (A2aHttpRouteConfig route : routes)
        {
            if (route.appliesToSkill(skillId))
            {
                route.guarded.stream()
                    .flatMap(guarded -> guarded.roles.stream())
                    .filter(role -> !roles.contains(role))
                    .forEach(roles::add);
            }
        }
        return roles.isEmpty() ? null : String.join(" ", roles);
    }

    public String match(
        float[][] phraseVectors,
        float[] input)
    {
        A2aHttpSkillConfig matched = null;
        double best = options.threshold;
        for (int i = 0; i < phraseSkills.size(); i++)
        {
            final double similarity = Vectors.similarity(input, phraseVectors[i]);
            if (similarity >= best)
            {
                best = similarity;
                matched = phraseSkills.get(i);
            }
        }
        return matched != null ? matched.id : null;
    }

    public String skillsSummary()
    {
        if (skillsSummary == null)
        {
            final StringBuilder summary = new StringBuilder();
            for (A2aHttpSkillConfig skill : skillsById.values())
            {
                if (summary.length() > 0)
                {
                    summary.append("; ");
                }
                summary.append(skill.id).append(": ").append(skill.description);
            }
            skillsSummary = summary.toString();
        }
        return skillsSummary;
    }

    public byte[] cardJson()
    {
        if (cardJson == null)
        {
            final A2aHttpCardConfig card = options.card;
            final JsonObjectBuilder object = Json.createObjectBuilder();
            object.add("name", card != null && card.name != null ? card.name : name);
            if (card != null && card.description != null)
            {
                object.add("description", card.description);
            }
            object.add("version", card != null && card.version != null ? card.version : DEFAULT_CARD_VERSION);
            object.add("capabilities", Json.createObjectBuilder()
                .add("streaming", true)
                .add("pushNotifications", false));
            object.add("defaultInputModes", strings(card != null ? card.defaultInputModes : null, DEFAULT_INPUT_MODES));
            object.add("defaultOutputModes", strings(card != null ? card.defaultOutputModes : null, DEFAULT_OUTPUT_MODES));

            final JsonArrayBuilder skills = Json.createArrayBuilder();
            for (A2aHttpSkillConfig skill : skillsById.values())
            {
                final JsonObjectBuilder entry = Json.createObjectBuilder()
                    .add("id", skill.id)
                    .add("name", skill.name != null ? skill.name : skill.id)
                    .add("description", skill.description)
                    .add("tags", strings(skill.tags, List.of()));
                if (skill.examples != null && !skill.examples.isEmpty())
                {
                    entry.add("examples", strings(skill.examples, List.of()));
                }
                skills.add(entry);
            }
            object.add("skills", skills);

            cardJson = object.build().toString().getBytes(StandardCharsets.UTF_8);
        }
        return cardJson;
    }

    public void resolveCredentials(
        long authorization,
        Map<String, String> headers)
    {
        final A2aHttpAuthorizationConfig authorizationConfig = options.authorization;
        if (authorizationConfig != null && authorizationConfig.headers != null && guard != null)
        {
            final String credentials = guard.credentials(authorization);
            final String identity = guard.identity(authorization);
            for (Map.Entry<String, String> entry : authorizationConfig.headers.entrySet())
            {
                final String value = substitute(entry.getValue(), credentials, identity);
                if (value != null)
                {
                    headers.put(entry.getKey(), value);
                }
            }
        }
    }

    public boolean credentialed()
    {
        return options.authorization != null && options.authorization.headers != null && guard != null;
    }

    public JsonSchema jsonSchema(
        ModelConfig model)
    {
        return model != null
            ? jsonSchemas.computeIfAbsent(model, resolveJsonSchemaFn)
            : null;
    }

    public String schemaText(
        ModelConfig model)
    {
        String text = null;
        if (model.cataloged != null && !model.cataloged.isEmpty())
        {
            final CatalogedConfig cataloged = model.cataloged.get(0);
            final long catalogId = cataloged.id != 0L
                ? cataloged.id
                : resolveId.applyAsLong(cataloged.name);
            final CatalogHandler handler = context.supplyCatalog(catalogId);

            if (handler != null && cataloged.schemas != null && !cataloged.schemas.isEmpty())
            {
                final SchemaConfig subject = cataloged.schemas.get(0);
                int schemaId = subject.id != CatalogHandler.NO_SCHEMA_ID
                    ? subject.id
                    : handler.resolve(subject.subject, subject.version);
                text = handler.resolve(schemaId);
            }
        }
        return text;
    }

    public List<String> unsatisfiedAccessors(
        A2aHttpRouteConfig route)
    {
        List<String> verdict = unsatisfiedAccessors.get(route);
        if (verdict == null)
        {
            List<String> unsatisfied = null;
            boolean deferred = false;

            if (route.skill != null && !route.argAccessors.isEmpty())
            {
                final A2aHttpSkillConfig skill = skillsById.get(route.skill);
                final ModelConfig input = skill != null ? skill.input : null;
                final String text = input != null ? schemaText(input) : null;
                if (input != null && (text == null || text.isBlank()))
                {
                    deferred = true;
                }
                else if (text != null)
                {
                    for (String accessor : route.argAccessors)
                    {
                        if (!argPathValid(text, accessor))
                        {
                            if (unsatisfied == null)
                            {
                                unsatisfied = new ArrayList<>();
                            }
                            unsatisfied.add("args." + accessor);
                        }
                    }
                }
            }

            verdict = unsatisfied != null ? unsatisfied : List.of();
            if (!deferred)
            {
                unsatisfiedAccessors.put(route, verdict);
            }
        }
        return verdict;
    }

    private JsonSchema resolveJsonSchema(
        ModelConfig model)
    {
        final String text = schemaText(model);
        return text != null ? JsonSchema.of(text) : null;
    }

    private static JsonArrayBuilder strings(
        List<String> values,
        List<String> defaults)
    {
        final JsonArrayBuilder array = Json.createArrayBuilder();
        (values != null ? values : defaults).forEach(array::add);
        return array;
    }

    private static String substitute(
        String template,
        String credentials,
        String identity)
    {
        String value = template;
        if (value.contains(CREDENTIALS_TOKEN))
        {
            value = credentials != null ? value.replace(CREDENTIALS_TOKEN, credentials) : null;
        }
        if (value != null && value.contains(IDENTITY_TOKEN))
        {
            value = identity != null ? value.replace(IDENTITY_TOKEN, identity) : null;
        }
        return value;
    }

    static boolean argPathValid(
        String schemaText,
        String dottedPath)
    {
        boolean valid = true;
        if (schemaText != null && !schemaText.isBlank())
        {
            try (JsonReader reader = Json.createReader(new StringReader(schemaText)))
            {
                final JsonStructure structure = reader.read();
                if (structure.getValueType() == JsonValue.ValueType.OBJECT)
                {
                    valid = pathExists(structure.asJsonObject(), dottedPath.split("\\."), 0);
                }
            }
            catch (Exception ex)
            {
                valid = true;
            }
        }
        return valid;
    }

    private static boolean pathExists(
        JsonObject node,
        String[] segments,
        int index)
    {
        boolean exists;
        final JsonValue additional = node.get("additionalProperties");
        final boolean openAdditional = additional != null &&
            (additional.getValueType() == JsonValue.ValueType.TRUE ||
             additional.getValueType() == JsonValue.ValueType.OBJECT);
        if (index >= segments.length)
        {
            exists = true;
        }
        else if (node.containsKey("$ref") || node.containsKey("allOf") || node.containsKey("anyOf") ||
            node.containsKey("oneOf") || node.containsKey("patternProperties") || node.containsKey("items") ||
            openAdditional)
        {
            exists = true;
        }
        else if (node.containsKey("properties") &&
            node.get("properties").getValueType() == JsonValue.ValueType.OBJECT)
        {
            final JsonObject properties = node.getJsonObject("properties");
            final String segment = segments[index];
            if (properties.containsKey(segment) &&
                properties.get(segment).getValueType() == JsonValue.ValueType.OBJECT)
            {
                exists = pathExists(properties.getJsonObject(segment), segments, index + 1);
            }
            else
            {
                exists = properties.containsKey(segment);
            }
        }
        else
        {
            exists = true;
        }
        return exists;
    }
}
