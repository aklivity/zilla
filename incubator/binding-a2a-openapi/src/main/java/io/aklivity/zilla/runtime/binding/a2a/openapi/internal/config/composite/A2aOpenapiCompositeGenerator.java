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
package io.aklivity.zilla.runtime.binding.a2a.openapi.internal.config.composite;

import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.json.JsonWriter;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import io.aklivity.zilla.config.binding.a2a.http.A2aHttpAuthorizationConfig;
import io.aklivity.zilla.config.binding.a2a.http.A2aHttpBindingConfig;
import io.aklivity.zilla.config.binding.a2a.http.A2aHttpBindingConfigBuilder;
import io.aklivity.zilla.config.binding.a2a.http.A2aHttpConditionConfig;
import io.aklivity.zilla.config.binding.a2a.http.A2aHttpOptionsConfig;
import io.aklivity.zilla.config.binding.a2a.http.A2aHttpRouteConfigBuilder;
import io.aklivity.zilla.config.binding.a2a.http.A2aHttpSkillConfig;
import io.aklivity.zilla.config.binding.a2a.http.A2aHttpWithConfig;
import io.aklivity.zilla.config.binding.a2a.openapi.A2aOpenapiAgentConfig;
import io.aklivity.zilla.config.binding.a2a.openapi.A2aOpenapiAuthorizationConfig;
import io.aklivity.zilla.config.binding.a2a.openapi.A2aOpenapiCatalogConfig;
import io.aklivity.zilla.config.binding.a2a.openapi.A2aOpenapiConditionConfig;
import io.aklivity.zilla.config.binding.a2a.openapi.A2aOpenapiSkillConfig;
import io.aklivity.zilla.config.binding.a2a.openapi.A2aOpenapiSpecificationConfig;
import io.aklivity.zilla.config.binding.a2a.openapi.A2aOpenapiWithConfig;
import io.aklivity.zilla.config.catalog.inline.InlineOptionsConfig;
import io.aklivity.zilla.config.catalog.inline.InlineOptionsConfigBuilder;
import io.aklivity.zilla.config.engine.GuardedConfig;
import io.aklivity.zilla.config.engine.GuardedConfigBuilder;
import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.engine.ModelConfigAdapter;
import io.aklivity.zilla.config.engine.NamespaceConfig;
import io.aklivity.zilla.config.engine.NamespaceConfigBuilder;
import io.aklivity.zilla.config.model.json.JsonModelConfig;
import io.aklivity.zilla.runtime.binding.a2a.openapi.internal.config.A2aOpenapiBindingConfig;
import io.aklivity.zilla.runtime.binding.a2a.openapi.internal.config.A2aOpenapiCompositeConfig;
import io.aklivity.zilla.runtime.binding.a2a.openapi.internal.config.A2aOpenapiCompositeRouteConfig;
import io.aklivity.zilla.runtime.binding.a2a.openapi.internal.config.A2aOpenapiRouteConfig;
import io.aklivity.zilla.runtime.common.json.JsonOverlay;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiExtension;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiParser;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiParserFactory;
import io.aklivity.zilla.runtime.common.openapi.security.GuardedRef;
import io.aklivity.zilla.runtime.common.openapi.security.GuardedResolution;
import io.aklivity.zilla.runtime.common.openapi.security.OpenapiGuardResolver;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiMediaTypeView;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiOperationView;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiParameterView;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiResponseView;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiSchemaView;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiSecurityRequirementView;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiServerView;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiView;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;

public final class A2aOpenapiCompositeGenerator
{
    private static final String CATALOG_NAME = "catalog0";
    private static final String BINDING_NAME = "a2a-http0";
    private static final String A2A_EXTENSION_NAME = "x-zilla-a2a";
    private static final String CONTENT_TYPE_EVENT_STREAM = "text/event-stream";
    private static final String HEADER_COOKIE = "cookie";
    private static final Pattern PATH_PARAM_PATTERN = Pattern.compile("\\{([^}]+)\\}");
    private static final Pattern JSON_CONTENT_TYPE_PATTERN = Pattern.compile("^application/(?:.+\\+)?json$");

    private final List<String> denied;
    private final Matcher jsonContentType;

    public A2aOpenapiCompositeGenerator()
    {
        this.denied = new ArrayList<>();
        this.jsonContentType = JSON_CONTENT_TYPE_PATTERN.matcher("");
    }

    public A2aOpenapiCompositeConfig generate(
        A2aOpenapiBindingConfig binding)
    {
        denied.clear();

        final OpenapiParser parser = new OpenapiParserFactory()
            .withExtension(OpenapiExtension.of(OpenapiExtension.Scope.OPERATION,
                A2A_EXTENSION_NAME, A2aEx.class))
            .createParser();
        final Map<String, OpenapiView> specsByLabel = new LinkedHashMap<>();
        final Map<String, Map<String, String>> securityByLabel = new LinkedHashMap<>();
        final Map<String, String> serverByLabel = new LinkedHashMap<>();
        JsonObject info = null;

        int tagIndex = 1;
        if (binding.options != null && binding.options.specs != null)
        {
            for (A2aOpenapiSpecificationConfig specification : binding.options.specs)
            {
                final String label = specification.label;
                securityByLabel.put(label, specification.security);
                serverByLabel.put(label, specification.server);
                for (A2aOpenapiCatalogConfig catalog : specification.catalogs)
                {
                    final long catalogId = binding.resolveId.applyAsLong(catalog.name);
                    final CatalogHandler handler = binding.supplyCatalog.apply(catalogId);
                    final int schemaId = handler.resolve(catalog.subject, catalog.version);
                    final String payload = handler.resolve(schemaId);
                    final JsonObject document = materialize(binding, catalog, payload);
                    final OpenapiView openapi = OpenapiView.of(tagIndex++, label, parser.parse(document.toString()));
                    validateSecurity(specification, openapi);
                    specsByLabel.put(label, openapi);

                    if (info == null && document.containsKey("info"))
                    {
                        info = document.getJsonObject("info");
                    }
                }
            }
        }

        final Set<OpenapiOperationView> claimed = new HashSet<>();
        final Set<String> usedNames = new HashSet<>();
        final List<RoutedOperation> routed = new LinkedList<>();
        for (A2aOpenapiRouteConfig route : binding.routes)
        {
            final A2aOpenapiWithConfig with = route.with;
            if (with == null)
            {
                continue;
            }
            final OpenapiView openapi = specsByLabel.get(with.spec);
            if (openapi == null)
            {
                throw new IllegalArgumentException("unknown spec \"%s\" in route".formatted(with.spec));
            }

            String skill = null;
            for (A2aOpenapiConditionConfig when : route.when)
            {
                if (when.skill != null)
                {
                    skill = when.skill;
                }
            }

            if (skill != null)
            {
                usedNames.add(skill);
            }

            final String exit = route.exit != null ? route.exit : binding.exit;

            for (OpenapiOperationView operation : candidateOperations(openapi, route, claimed))
            {
                claimed.add(operation);

                final String id = skill != null ? skill : A2aOpenapiSkillNamer.defaultName(operation, usedNames);

                final String reason = denialReason(operation, with, serverByLabel.get(with.spec));
                final GuardedResolution resolution = reason == null
                    ? resolveGuarded(binding, operation, with.spec, securityByLabel.get(with.spec))
                    : null;
                if (reason != null)
                {
                    denied.add(reason);
                }
                else if (resolution.denied())
                {
                    denied.add(resolution.reason);
                }
                else
                {
                    routed.add(new RoutedOperation(id, operation, combineGuarded(route, resolution.guarded),
                        exit, serverByLabel.get(with.spec), with.params));
                }
            }
        }

        final JsonObject agentInfo = info;
        final NamespaceConfig namespace = NamespaceConfig.builder()
            .name("%s/a2a-http".formatted(binding.qname))
            .inject(n -> injectCatalog(n, routed))
            .inject(n -> injectBinding(n, binding, agentInfo, routed))
            .build();

        final List<A2aOpenapiCompositeRouteConfig> routes = new LinkedList<>();
        namespace.bindings.stream()
            .filter(b -> BINDING_NAME.equals(b.name))
            .forEach(b ->
            {
                final long routeId = binding.supplyBindingId.applyAsLong(namespace, b);
                routes.add(new A2aOpenapiCompositeRouteConfig(routeId));
            });

        return new A2aOpenapiCompositeConfig(List.of(namespace), routes);
    }

    public List<String> deniedOperations()
    {
        return denied;
    }

    private JsonObject materialize(
        A2aOpenapiBindingConfig binding,
        A2aOpenapiCatalogConfig catalog,
        String payload)
    {
        JsonObject document = YamlJson.createReader(new StringReader(payload)).readObject();
        if (catalog.overlay != null)
        {
            final long catalogId = binding.resolveId.applyAsLong(catalog.overlay.name);
            final CatalogHandler handler = binding.supplyCatalog.apply(catalogId);
            final int schemaId = handler.resolve(catalog.overlay.schema.subject, catalog.overlay.schema.version);
            final String overlayPayload = handler.resolve(schemaId);

            final JsonObject overlayDocument = YamlJson.createReader(new StringReader(overlayPayload)).readObject();
            document = JsonOverlay.of(overlayDocument).apply(document).asJsonObject();
        }

        return document;
    }

    private static void validateSecurity(
        A2aOpenapiSpecificationConfig specification,
        OpenapiView openapi)
    {
        if (specification.security != null)
        {
            for (String scheme : specification.security.keySet())
            {
                final boolean declared = openapi.components != null &&
                    openapi.components.securitySchemes != null &&
                    openapi.components.securitySchemes.containsKey(scheme);
                if (!declared)
                {
                    throw new IllegalArgumentException(
                        "security scheme \"%s\" is not declared by spec \"%s\"".formatted(scheme, specification.label));
                }
            }
        }
    }

    private String denialReason(
        OpenapiOperationView operation,
        A2aOpenapiWithConfig with,
        String server)
    {
        String reason = null;

        if (operation.hasRequestBody() && requestBodyContent(operation) == null)
        {
            reason = "operation \"%s\" in spec \"%s\" has unsupported request content type (%s); only JSON is supported"
                .formatted(operation.id, with.spec, String.join(", ", operation.requestBody.content.keySet()));
        }

        if (reason == null && operation.parameters != null)
        {
            for (OpenapiParameterView parameter : operation.parameters)
            {
                if (!supportedLocation(parameter.in))
                {
                    reason = "operation \"%s\" in spec \"%s\" has unsupported parameter location \"%s\" for parameter \"%s\""
                        .formatted(operation.id, with.spec, parameter.in, parameter.name);
                }
                else if (parameter.schema != null && structuredSchema(parameter.schema))
                {
                    reason = "operation \"%s\" in spec \"%s\" has unsupported serialization of parameter \"%s\" (%s)"
                        .formatted(operation.id, with.spec, parameter.name, parameter.schema.type);
                }

                if (reason != null)
                {
                    break;
                }
            }
        }

        if (reason == null && resolveServer(operation, server) == null)
        {
            reason = "operation \"%s\" in spec \"%s\" has no server url; configure options.specs.%s.server"
                .formatted(operation.id, with.spec, with.spec);
        }

        return reason;
    }

    private static boolean supportedLocation(
        String location)
    {
        return "path".equals(location) || "query".equals(location) ||
            "header".equals(location) || "cookie".equals(location);
    }

    private static boolean structuredSchema(
        OpenapiSchemaView schema)
    {
        return "array".equals(schema.type) || "object".equals(schema.type);
    }

    // a route can reference only one guard, so an OR-alternative or several distinct guards are denied by the
    // shared resolver; unlike a plain pass-through, a required scheme without a configured guard is denied
    // as well, so that no operation is ever exposed without the protection its OpenAPI security requires
    private GuardedResolution resolveGuarded(
        A2aOpenapiBindingConfig binding,
        OpenapiOperationView operation,
        String label,
        Map<String, String> securityByScheme)
    {
        GuardedResolution resolution = OpenapiGuardResolver.resolve(
            operation.id, label, operation.security, securityByScheme,
            binding.resolveId, binding.supplyQName);

        if (!resolution.denied() && operation.security != null && operation.security.size() == 1)
        {
            for (OpenapiSecurityRequirementView requirement : operation.security.get(0))
            {
                if (securityByScheme == null || !securityByScheme.containsKey(requirement.name))
                {
                    resolution = GuardedResolution.denied(
                        "operation \"%s\" in spec \"%s\" requires security scheme \"%s\" which has no guard configured"
                            .formatted(operation.id, label, requirement.name));
                    break;
                }
            }
        }

        return resolution;
    }

    private <C> NamespaceConfigBuilder<C> injectCatalog(
        NamespaceConfigBuilder<C> namespace,
        List<RoutedOperation> routed)
    {
        namespace
            .catalog()
                .name(CATALOG_NAME)
                .type("inline")
                .options(InlineOptionsConfig::builder)
                    .inject(o -> injectSubjects(o, routed))
                    .build()
                .build();

        return namespace;
    }

    private <C> InlineOptionsConfigBuilder<C> injectSubjects(
        InlineOptionsConfigBuilder<C> options,
        List<RoutedOperation> routed)
    {
        try (Jsonb jsonb = JsonbBuilder.create())
        {
            for (RoutedOperation entry : routed)
            {
                final OpenapiOperationView operation = entry.operation;

                // derived unconditionally, even when input/output is overridden, the derived subject
                // then simply goes unreferenced
                options.schema()
                    .subject("%s-input".formatted(entry.id))
                    .version("latest")
                    .schema(inputSchema(operation))
                    .build();

                if (operation.hasRequestBody())
                {
                    options.schema()
                        .subject("%s-body".formatted(entry.id))
                        .version("latest")
                        .schema(bodySchema(jsonb, operation))
                        .build();
                }

                final OpenapiMediaTypeView output = outputContent(operation);
                if (output != null)
                {
                    options.schema()
                        .subject("%s-output".formatted(entry.id))
                        .version("latest")
                        .schema(toSchemaJson(jsonb, output.schema.model))
                        .build();
                }
            }
        }
        catch (Exception ex)
        {
            rethrowUnchecked(ex);
        }

        return options;
    }

    private <C> NamespaceConfigBuilder<C> injectBinding(
        NamespaceConfigBuilder<C> namespace,
        A2aOpenapiBindingConfig binding,
        JsonObject info,
        List<RoutedOperation> routed)
    {
        return namespace
            .binding(A2aHttpBindingConfig::builder)
                .name(BINDING_NAME)
                .kind(binding.kind)
                .options(a2aHttpOptions(binding, info, routed))
                .inject(b -> injectRoutes(b, routed))
                .build();
    }

    private A2aHttpOptionsConfig a2aHttpOptions(
        A2aOpenapiBindingConfig binding,
        JsonObject info,
        List<RoutedOperation> routed)
    {
        final List<A2aHttpSkillConfig> skills = new ArrayList<>();
        boolean streaming = false;

        for (RoutedOperation entry : routed)
        {
            final A2aOpenapiSkillConfig override = skillOverride(binding, entry.id);
            final A2aEx extension = entry.operation.extension(A2A_EXTENSION_NAME, A2aEx.class).orElse(null);

            final ModelConfig input = override != null && override.input != null
                ? qualifyModel(binding, override.input)
                : jsonModel("%s-input".formatted(entry.id));
            final ModelConfig output = override != null && override.output != null
                ? qualifyModel(binding, override.output)
                : outputContent(entry.operation) != null ? jsonModel("%s-output".formatted(entry.id)) : null;

            skills.add(A2aHttpSkillConfig.builder()
                .id(entry.id)
                .name(skillName(override, extension, entry.operation))
                .description(skillDescription(override, extension, entry.operation))
                .examples(skillExamples(override, extension))
                .tags(skillTags(override, extension, entry.operation))
                .input(input)
                .output(output)
                .build());

            streaming |= offersEventStream(entry.operation);
        }

        final A2aOpenapiAgentConfig agent = binding.options != null ? binding.options.agent : null;

        return A2aHttpOptionsConfig.builder()
            .embedding(qualify(binding, binding.options.embedding))
            .store(qualify(binding, binding.options.store))
            .threshold(binding.options.threshold)
            .card()
                .name(agent != null && agent.name != null ? agent.name : text(info, "title"))
                .description(agent != null && agent.description != null ? agent.description : text(info, "description"))
                .version(agent != null && agent.version != null ? agent.version : text(info, "version"))
                .streaming(streaming)
                .build()
            .authorization(a2aHttpAuthorization(binding))
            .skills(skills)
            .build();
    }

    private static String text(
        JsonObject object,
        String name)
    {
        return object != null && object.containsKey(name) && object.get(name).getValueType() == JsonValue.ValueType.STRING
            ? object.getString(name)
            : null;
    }

    private static String qualify(
        A2aOpenapiBindingConfig binding,
        String name)
    {
        return binding.supplyQName.apply(binding.resolveId.applyAsLong(name));
    }

    private static A2aOpenapiSkillConfig skillOverride(
        A2aOpenapiBindingConfig binding,
        String id)
    {
        return binding.options.skills.stream()
            .filter(s -> id.equals(s.id))
            .findFirst()
            .orElse(null);
    }

    // Name precedence: authored override (options.skills.<id>.name) > x-zilla-a2a.name > OpenAPI summary,
    // otherwise left unset so that a2a-http defaults it to the skill id
    private static String skillName(
        A2aOpenapiSkillConfig override,
        A2aEx extension,
        OpenapiOperationView operation)
    {
        return override != null && override.name != null
            ? override.name
            : extension != null && extension.name != null
                ? extension.name
                : operation.summary;
    }

    // Description precedence: authored override > x-zilla-a2a.description > OpenAPI description > OpenAPI summary >
    // operation id. a2a-http embeds the description and the examples to select a skill from an intent, and
    // requires a description
    private static String skillDescription(
        A2aOpenapiSkillConfig override,
        A2aEx extension,
        OpenapiOperationView operation)
    {
        return override != null && override.description != null
            ? override.description
            : extension != null && extension.description != null
                ? extension.description
                : operation.description != null
                    ? operation.description
                    : operation.summary != null
                        ? operation.summary
                        : operation.id;
    }

    private static List<String> skillExamples(
        A2aOpenapiSkillConfig override,
        A2aEx extension)
    {
        return override != null && override.examples != null
            ? override.examples
            : extension != null ? extension.examples : null;
    }

    private static List<String> skillTags(
        A2aOpenapiSkillConfig override,
        A2aEx extension,
        OpenapiOperationView operation)
    {
        return override != null && override.tags != null
            ? override.tags
            : extension != null && extension.tags != null
                ? extension.tags
                : operation.tags;
    }

    private A2aHttpAuthorizationConfig a2aHttpAuthorization(
        A2aOpenapiBindingConfig binding)
    {
        final A2aOpenapiAuthorizationConfig authorization = binding.options.authorization;

        return authorization != null
            ? A2aHttpAuthorizationConfig.builder()
                .name(authorization.qname)
                .headers(authorization.headers)
                .build()
            : null;
    }

    private <C> A2aHttpBindingConfigBuilder<C> injectRoutes(
        A2aHttpBindingConfigBuilder<C> a2aHttp,
        List<RoutedOperation> routed)
    {
        for (RoutedOperation entry : routed)
        {
            a2aHttp.route()
                .when(A2aHttpConditionConfig.builder().skill(entry.id).build())
                .with(withConfig(entry))
                .exit(entry.exit)
                .inject(route -> injectGuarded(route, entry))
                .build();
        }

        return a2aHttp;
    }

    private <C> A2aHttpRouteConfigBuilder<C> injectGuarded(
        A2aHttpRouteConfigBuilder<C> route,
        RoutedOperation entry)
    {
        for (GuardedRef ref : entry.guarded)
        {
            route.guarded()
                .name(ref.qname)
                .inject(g -> injectRoles(g, ref.roles))
                .build();
        }

        return route;
    }

    private <C> GuardedConfigBuilder<C> injectRoles(
        GuardedConfigBuilder<C> guarded,
        List<String> roles)
    {
        roles.forEach(guarded::role);
        return guarded;
    }

    private static List<OpenapiOperationView> candidateOperations(
        OpenapiView openapi,
        A2aOpenapiRouteConfig route,
        Set<OpenapiOperationView> claimed)
    {
        final A2aOpenapiWithConfig with = route.with;
        final List<OpenapiOperationView> candidates = route.isBulk()
            ? bulkCandidates(openapi, with)
            : explicitCandidate(openapi, with);

        return candidates.stream()
            .filter(operation -> !claimed.contains(operation))
            .toList();
    }

    private static List<OpenapiOperationView> explicitCandidate(
        OpenapiView openapi,
        A2aOpenapiWithConfig with)
    {
        final OpenapiOperationView operation = openapi.operations != null
            ? openapi.operations.get(with.operation)
            : null;

        return operation != null ? List.of(operation) : List.of();
    }

    private static List<OpenapiOperationView> bulkCandidates(
        OpenapiView openapi,
        A2aOpenapiWithConfig with)
    {
        final Predicate<OpenapiOperationView> matches;
        if (with.tag != null)
        {
            matches = operation -> operation.tags != null && operation.tags.contains(with.tag);
        }
        else if (with.operation != null)
        {
            final Pattern pattern = compileGlob(with.operation);
            matches = operation -> operation.id != null && pattern.matcher(operation.id).matches();
        }
        else
        {
            matches = operation -> true;
        }

        return allOperations(openapi).stream()
            .filter(matches)
            .sorted(Comparator.comparing((OpenapiOperationView operation) -> operation.path)
                .thenComparing(operation -> operation.method))
            .toList();
    }

    private static List<OpenapiOperationView> allOperations(
        OpenapiView openapi)
    {
        return openapi.paths == null
            ? List.of()
            : openapi.paths.values().stream()
                .flatMap(path -> path.methods.values().stream())
                .toList();
    }

    private static Pattern compileGlob(
        String glob)
    {
        final StringBuilder regex = new StringBuilder();
        final String[] literals = glob.split("\\*", -1);
        for (int index = 0; index < literals.length; index++)
        {
            if (index > 0)
            {
                regex.append(".*");
            }
            if (!literals[index].isEmpty())
            {
                regex.append(Pattern.quote(literals[index]));
            }
        }
        return Pattern.compile(regex.toString());
    }

    private static List<GuardedRef> combineGuarded(
        A2aOpenapiRouteConfig route,
        List<GuardedRef> derived)
    {
        List<GuardedRef> combined = derived;

        if (!route.guarded.isEmpty())
        {
            final Map<String, List<String>> rolesByQName = new LinkedHashMap<>();
            for (GuardedRef ref : derived)
            {
                rolesByQName.computeIfAbsent(ref.qname, q -> new ArrayList<>()).addAll(ref.roles);
            }
            for (GuardedConfig guarded : route.guarded)
            {
                final String qname = guarded.qname();
                rolesByQName.computeIfAbsent(qname, q -> new ArrayList<>()).addAll(guarded.roles);
            }

            combined = rolesByQName.entrySet().stream()
                .map(e -> new GuardedRef(e.getKey(), e.getValue().stream().distinct().toList()))
                .toList();
        }

        return combined;
    }

    private A2aHttpWithConfig withConfig(
        RoutedOperation entry)
    {
        final OpenapiOperationView operation = entry.operation;
        final ResolvedServer resolved = resolveServer(operation, entry.server);

        final StringBuilder path = new StringBuilder(resolved.base);
        path.append(lowerPathParams(operation.path, entry.params));

        final String query = queryString(operation, entry.params);
        if (!query.isEmpty())
        {
            path.append('?').append(query);
        }

        final Map<String, String> headers = new LinkedHashMap<>();
        headers.put(":method", operation.method);
        headers.put(":scheme", resolved.scheme);
        headers.put(":authority", resolved.authority);
        headers.put(":path", path.toString());
        injectHeaderParams(headers, operation, entry.params);
        injectCookieParams(headers, operation, entry.params);

        final ModelConfig body = operation.hasRequestBody()
            ? jsonModel("%s-body".formatted(entry.id))
            : null;

        return A2aHttpWithConfig.builder()
            .headers(headers)
            .body(body)
            .build();
    }

    private static ResolvedServer resolveServer(
        OpenapiOperationView operation,
        String override)
    {
        final URI uri = override != null
            ? URI.create(override)
            : operation.servers != null && !operation.servers.isEmpty()
                ? serverUri(operation.servers.get(0))
                : null;

        ResolvedServer resolved = null;
        if (uri != null && uri.getScheme() != null && uri.getHost() != null)
        {
            final int port = uri.getPort() != -1 ? uri.getPort() : defaultPort(uri.getScheme());
            final String authority = port != -1
                ? "%s:%d".formatted(uri.getHost(), port)
                : uri.getHost();
            final String path = uri.getPath() != null ? uri.getPath() : "";
            final String base = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;

            resolved = new ResolvedServer(uri.getScheme(), authority, base);
        }

        return resolved;
    }

    private static URI serverUri(
        OpenapiServerView server)
    {
        return server.url;
    }

    private static int defaultPort(
        String scheme)
    {
        return switch (scheme)
        {
        case "http" -> 80;
        case "https" -> 443;
        default -> -1;
        };
    }

    private static String lowerPathParams(
        String path,
        Map<String, String> params)
    {
        String result = path;
        if (path != null)
        {
            final Matcher matcher = PATH_PARAM_PATTERN.matcher(path);
            final StringBuilder builder = new StringBuilder();
            int last = 0;
            while (matcher.find())
            {
                builder.append(path, last, matcher.start());
                builder.append("${").append(paramExpression(matcher.group(1), params)).append('}');
                last = matcher.end();
            }
            builder.append(path, last, path.length());
            result = builder.toString();
        }
        return result;
    }

    private static String paramExpression(
        String name,
        Map<String, String> params)
    {
        final String override = params.get(name);
        return override != null ? innerExpression(override) : "args.%s".formatted(name);
    }

    private static String innerExpression(
        String expression)
    {
        String inner = expression.trim();
        if (inner.startsWith("${") && inner.endsWith("}"))
        {
            inner = inner.substring(2, inner.length() - 1);
        }
        return inner;
    }

    private static String queryString(
        OpenapiOperationView operation,
        Map<String, String> params)
    {
        final StringBuilder query = new StringBuilder();
        if (operation.parameters != null)
        {
            for (OpenapiParameterView parameter : operation.parameters)
            {
                if ("query".equals(parameter.in))
                {
                    if (query.length() > 0)
                    {
                        query.append('&');
                    }
                    final String expression = paramExpression(parameter.name, params);
                    if (parameter.required)
                    {
                        query.append(parameter.name)
                            .append("=${")
                            .append(expression)
                            .append('}');
                    }
                    else
                    {
                        query.append("${?")
                            .append(expression)
                            .append('=')
                            .append(parameter.name)
                            .append('}');
                    }
                }
            }
        }
        return query.toString();
    }

    // a2a-http resolves every ${args.x} of a header as a required input, so only a required header
    // parameter can be mapped; an optional one is left out of the skill rather than turned into a required input
    private static void injectHeaderParams(
        Map<String, String> headers,
        OpenapiOperationView operation,
        Map<String, String> params)
    {
        if (operation.parameters != null)
        {
            for (OpenapiParameterView parameter : operation.parameters)
            {
                if ("header".equals(parameter.in) && parameter.required)
                {
                    headers.put(parameter.name.toLowerCase(), "${%s}".formatted(paramExpression(parameter.name, params)));
                }
            }
        }
    }

    // Cookie names, unlike header names, are case-sensitive (RFC 6265) -- keep the OpenAPI-declared
    // casing verbatim; the required cookie parameters share a single cookie header
    private static void injectCookieParams(
        Map<String, String> headers,
        OpenapiOperationView operation,
        Map<String, String> params)
    {
        final List<String> cookies = new ArrayList<>();
        if (operation.parameters != null)
        {
            for (OpenapiParameterView parameter : operation.parameters)
            {
                if ("cookie".equals(parameter.in) && parameter.required)
                {
                    cookies.add("%s=${%s}".formatted(parameter.name, paramExpression(parameter.name, params)));
                }
            }
        }

        if (!cookies.isEmpty())
        {
            headers.put(HEADER_COOKIE, String.join("; ", cookies));
        }
    }

    private static ModelConfig jsonModel(
        String subject)
    {
        return JsonModelConfig.builder()
            .catalog()
                .name(CATALOG_NAME)
                .schema()
                    .subject(subject)
                    .version("latest")
                    .build()
                .build()
            .build();
    }

    private static ModelConfig qualifyModel(
        A2aOpenapiBindingConfig binding,
        ModelConfig model)
    {
        // an authored input/output's catalog reference names a catalog in the caller's own namespace
        // (e.g. "catalog0"), but this ModelConfig is forwarded as-is into the generated a2a-http binding,
        // which lives in its own freshly-generated namespace that has its own, unrelated, same-named
        // "catalog0" -- rewrite the reference to be namespace-qualified (qname) so it resolves absolutely,
        // against the caller's namespace, from anywhere
        ModelConfig qualified = model;
        if (model.cataloged != null && !model.cataloged.isEmpty())
        {
            final ModelConfigAdapter adapter = new ModelConfigAdapter();
            adapter.adaptType(model.model);
            final JsonValue value = adapter.adaptToJson(model);
            if (value instanceof JsonObject && ((JsonObject) value).containsKey("catalog"))
            {
                final JsonObject object = (JsonObject) value;
                final JsonObject catalog = object.getJsonObject("catalog");
                final JsonObjectBuilder qualifiedCatalog = Json.createObjectBuilder();
                for (Map.Entry<String, JsonValue> entry : catalog.entrySet())
                {
                    qualifiedCatalog.add(qualify(binding, entry.getKey()), entry.getValue());
                }
                final JsonObject rewritten = Json.createObjectBuilder(object)
                    .remove("catalog")
                    .add("catalog", qualifiedCatalog)
                    .build();
                qualified = adapter.adaptFromJson(rewritten);
            }
        }
        return qualified;
    }

    // the lowest 2xx status wins, so that the choice does not depend on map iteration order
    private static List<OpenapiResponseView> successResponses(
        OpenapiOperationView operation)
    {
        final Map<String, OpenapiResponseView> successes = new TreeMap<>();
        if (operation.hasResponses())
        {
            operation.responses.forEach((status, response) ->
            {
                if (status != null && status.startsWith("2"))
                {
                    successes.put(status, response);
                }
            });
        }
        return new ArrayList<>(successes.values());
    }

    private boolean offersEventStream(
        OpenapiOperationView operation)
    {
        return successResponses(operation).stream()
            .anyMatch(response -> response.content != null && response.content.containsKey(CONTENT_TYPE_EVENT_STREAM));
    }

    // the output schema comes from the first JSON content of the lowest 2xx response that declares one,
    // preferring application/json over other JSON media types
    private OpenapiMediaTypeView outputContent(
        OpenapiOperationView operation)
    {
        OpenapiMediaTypeView output = null;
        for (OpenapiResponseView response : successResponses(operation))
        {
            if (output == null && response.content != null)
            {
                output = jsonContent(response.content.values().stream()
                    .filter(typed -> typed.schema != null)
                    .toList());
            }
        }
        return output;
    }

    private OpenapiMediaTypeView requestBodyContent(
        OpenapiOperationView operation)
    {
        return jsonContent(operation.requestBody.content.values().stream().toList());
    }

    private OpenapiMediaTypeView jsonContent(
        List<OpenapiMediaTypeView> contents)
    {
        final List<OpenapiMediaTypeView> json = contents.stream()
            .filter(typed -> jsonContentType.reset(typed.name).matches())
            .sorted(Comparator.comparing((OpenapiMediaTypeView typed) -> !"application/json".equals(typed.name))
                .thenComparing(typed -> typed.name))
            .toList();
        return json.isEmpty() ? null : json.get(0);
    }

    private String inputSchema(
        OpenapiOperationView operation)
    {
        final JsonObjectBuilder properties = Json.createObjectBuilder();
        final List<String> required = new LinkedList<>();
        final List<String> propertyNames = new LinkedList<>();
        JsonValue additionalProperties = null;

        if (operation.parameters != null)
        {
            for (OpenapiParameterView parameter : operation.parameters)
            {
                final boolean mapped = "path".equals(parameter.in) || "query".equals(parameter.in) ||
                    parameter.required && ("header".equals(parameter.in) || "cookie".equals(parameter.in));
                if (mapped && parameter.schema != null)
                {
                    properties.add(parameter.name, schemaObject(parameter.schema));
                    propertyNames.add(parameter.name);
                    if (parameter.required)
                    {
                        required.add(parameter.name);
                    }
                }
            }
        }

        final OpenapiMediaTypeView content = operation.hasRequestBody() ? requestBodyContent(operation) : null;
        final OpenapiSchemaView schema = content != null ? content.schema : null;
        if (schema != null)
        {
            if (schema.properties != null)
            {
                for (Map.Entry<String, OpenapiSchemaView> entry : new TreeMap<>(schema.properties).entrySet())
                {
                    String key = entry.getKey();
                    if (propertyNames.contains(key))
                    {
                        key = "%s_body".formatted(key);
                    }
                    properties.add(key, schemaObject(entry.getValue()));
                }
                if (schema.required != null)
                {
                    for (String req : schema.required)
                    {
                        String key = propertyNames.contains(req) ? "%s_body".formatted(req) : req;
                        required.add(key);
                    }
                }
            }
            if (schema.model.additionalProperties != null &&
                !JsonValue.FALSE.equals(schema.model.additionalProperties))
            {
                additionalProperties = schema.model.additionalProperties;
            }
        }

        final JsonObjectBuilder object = Json.createObjectBuilder();
        object.add("type", "object");
        object.add("properties", properties);
        if (!required.isEmpty())
        {
            final JsonArrayBuilder requiredArray = Json.createArrayBuilder();
            required.forEach(requiredArray::add);
            object.add("required", requiredArray);
        }
        if (additionalProperties != null)
        {
            object.add("additionalProperties", additionalProperties);
        }

        return object.build().toString();
    }

    private String bodySchema(
        Jsonb jsonb,
        OpenapiOperationView operation)
    {
        final OpenapiMediaTypeView content = requestBodyContent(operation);

        return content != null && content.schema != null
            ? denyParameterNames(toSchemaJson(jsonb, content.schema.model), operation)
            : "{\"type\":\"object\"}";
    }

    // A structured body schema (named properties, possibly widened by additionalProperties) shares the
    // same skill arguments object as the operation's own path/query/header/cookie parameters. Once
    // additionalProperties admits unnamed keys, a parameter name would otherwise pass straight through
    // that wildcard into the outbound body alongside the fields it actually describes -- so every
    // parameter not already a declared body property is denied explicitly (a false sub-schema), which
    // JsonSchema#rejectedPaths() surfaces to the body projector as always winning over the wildcard.
    private static String denyParameterNames(
        String schemaJson,
        OpenapiOperationView operation)
    {
        String result = schemaJson;
        if (operation.parameters != null && !operation.parameters.isEmpty())
        {
            JsonValue parsed = Json.createReader(new StringReader(schemaJson)).readValue();
            if (parsed instanceof JsonObject)
            {
                JsonObject schema = (JsonObject) parsed;
                JsonValue properties = schema.get("properties");
                if (properties instanceof JsonObject)
                {
                    JsonObject declared = (JsonObject) properties;
                    JsonObjectBuilder rejected = Json.createObjectBuilder(declared);
                    boolean modified = false;
                    for (OpenapiParameterView parameter : operation.parameters)
                    {
                        if (!declared.containsKey(parameter.name))
                        {
                            rejected.add(parameter.name, JsonValue.FALSE);
                            modified = true;
                        }
                    }
                    if (modified)
                    {
                        result = Json.createObjectBuilder(schema)
                            .add("properties", rejected)
                            .build()
                            .toString();
                    }
                }
            }
        }
        return result;
    }

    private static JsonObject schemaObject(
        OpenapiSchemaView schema)
    {
        final JsonObjectBuilder object = Json.createObjectBuilder();
        if (schema.type != null)
        {
            object.add("type", schema.type);
        }
        if (schema.format != null)
        {
            object.add("format", schema.format);
        }
        return object.build();
    }

    private static String toSchemaJson(
        Jsonb jsonb,
        OpenapiSchemaView.OpenapiJsonSchema schema)
    {
        String schemaJson = jsonb.toJson(schema);

        JsonReader reader = Json.createReader(new StringReader(schemaJson));
        JsonValue jsonValue = reader.readValue();

        if (jsonValue instanceof JsonObject)
        {
            JsonObject jsonObject = (JsonObject) jsonValue;
            if (jsonObject.containsKey("schema"))
            {
                JsonValue modifiedJsonValue = jsonObject.get("schema");
                StringWriter stringWriter = new StringWriter();
                JsonWriter jsonWriter = Json.createWriter(stringWriter);
                jsonWriter.write(modifiedJsonValue);
                jsonWriter.close();
                schemaJson = stringWriter.toString();
            }
        }

        return schemaJson;
    }

    private static final class RoutedOperation
    {
        private final String id;
        private final OpenapiOperationView operation;
        private final List<GuardedRef> guarded;
        private final String exit;
        private final String server;
        private final Map<String, String> params;

        private RoutedOperation(
            String id,
            OpenapiOperationView operation,
            List<GuardedRef> guarded,
            String exit,
            String server,
            Map<String, String> params)
        {
            this.id = id;
            this.operation = operation;
            this.guarded = guarded;
            this.exit = exit;
            this.server = server;
            this.params = params != null ? params : Map.of();
        }
    }

    private static final class ResolvedServer
    {
        private final String scheme;
        private final String authority;
        private final String base;

        private ResolvedServer(
            String scheme,
            String authority,
            String base)
        {
            this.scheme = scheme;
            this.authority = authority;
            this.base = base;
        }
    }
}
