/*
 * Copyright 2021-2022 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.internal.registry;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;
import java.util.function.ToIntFunction;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonPatch;
import jakarta.json.JsonReader;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonParser;

import org.leadpony.justify.api.JsonSchema;
import org.leadpony.justify.api.JsonSchemaReader;
import org.leadpony.justify.api.JsonValidationService;
import org.leadpony.justify.api.ProblemHandler;

import io.aklivity.zilla.runtime.engine.Engine;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.GuardConfig;
import io.aklivity.zilla.runtime.engine.config.GuardedConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;
import io.aklivity.zilla.runtime.engine.config.VaultConfig;
import io.aklivity.zilla.runtime.engine.expression.ExpressionResolver;
import io.aklivity.zilla.runtime.engine.ext.EngineExtContext;
import io.aklivity.zilla.runtime.engine.ext.EngineExtSpi;
import io.aklivity.zilla.runtime.engine.guard.Guard;
import io.aklivity.zilla.runtime.engine.internal.Tuning;
import io.aklivity.zilla.runtime.engine.internal.config.NamespaceAdapter;
import io.aklivity.zilla.runtime.engine.internal.registry.json.UniquePropertyKeysSchema;
import io.aklivity.zilla.runtime.engine.internal.stream.NamespacedId;
import static java.net.http.HttpClient.Redirect.NORMAL;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.agrona.LangUtil.rethrowUnchecked;

public class ConfigurationManager
{
    protected static final String CONFIG_TEXT_DEFAULT = "{\n  \"name\": \"default\"\n}\n";
    private final Collection<URL> schemaTypes;
    private final Function<String, Guard> guardByType;
    private final ToIntFunction<String> supplyId;
    private final IntFunction<ToIntFunction<KindConfig>> maxWorkers;
    private final Tuning tuning;
    private final Collection<DispatchAgent> dispatchers;
    private final Consumer<String> logger;
    private final EngineExtContext context;
    private final EngineConfiguration config;
    private final List<EngineExtSpi> extensions;
    private final ExpressionResolver expressions;
    private final Consumer<URL> handleConfigURL;

    public ConfigurationManager(
        Collection<URL> schemaTypes,
        Function<String, Guard> guardByType,
        ToIntFunction<String> supplyId,
        IntFunction<ToIntFunction<KindConfig>> maxWorkers,
        Tuning tuning,
        Collection<DispatchAgent> dispatchers,
        Consumer<String> logger,
        EngineExtContext context,
        EngineConfiguration config,
        List<EngineExtSpi> extensions,
        Consumer<URL> handleConfigURL)
    {
        this.schemaTypes = schemaTypes;
        this.guardByType = guardByType;
        this.supplyId = supplyId;
        this.maxWorkers = maxWorkers;
        this.tuning = tuning;
        this.dispatchers = dispatchers;
        this.logger = logger;
        this.context = context;
        this.config = config;
        this.extensions = extensions;
        this.handleConfigURL = handleConfigURL;
        this.expressions = ExpressionResolver.instantiate();
    }

    public NamespaceConfig parse(
        String configText)
    {
        NamespaceConfig namespace = null;
        if (configText == null || configText.isEmpty())
        {
            configText = CONFIG_TEXT_DEFAULT;
        }
        else
        {
            try
            {
                configText = readURL(configURL);
            }
            catch (Exception ex)
            {
                configText = CONFIG_TEXT_DEFAULT;
            }
        }

        if (!configText.endsWith(System.lineSeparator()))
        {
            configText += System.lineSeparator();
        }
        logger.accept(configText);

        if (config.configResolveExpressions())
        {
            configText = expressions.resolve(configText);
        }

        List<String> errors = new LinkedList<>();
        parse:
        try
        {
            //TODO: detect configURLs and call handleConfigURL
            InputStream schemaInput = Engine.class.getResourceAsStream("internal/schema/engine.schema.json");

            JsonProvider schemaProvider = JsonProvider.provider();
            JsonReader schemaReader = schemaProvider.createReader(schemaInput);
            JsonObject schemaObject = schemaReader.readObject();

            for (URL schemaType : schemaTypes)
            {
                InputStream schemaPatchInput = schemaType.openStream();
                JsonReader schemaPatchReader = schemaProvider.createReader(schemaPatchInput);
                JsonArray schemaPatchArray = schemaPatchReader.readArray();
                JsonPatch schemaPatch = schemaProvider.createPatch(schemaPatchArray);

                schemaObject = schemaPatch.apply(schemaObject);
            }

            JsonParser schemaParser = schemaProvider.createParserFactory(null)
                .createParser(new StringReader(schemaObject.toString()));

            JsonValidationService service = JsonValidationService.newInstance();
            ProblemHandler handler = service.createProblemPrinter(errors::add);
            JsonSchemaReader reader = service.createSchemaReader(schemaParser);
            JsonSchema schema = new UniquePropertyKeysSchema(reader.read());

            JsonProvider provider = service.createJsonProvider(schema, parser -> handler);
            provider.createReader(new StringReader(configText)).read();

            if (!errors.isEmpty())
            {
                break parse;
            }

            JsonbConfig config = new JsonbConfig()
                .withAdapters(new NamespaceAdapter());
            Jsonb jsonb = JsonbBuilder.newBuilder()
                .withProvider(provider)
                .withConfig(config)
                .build();

            namespace = jsonb.fromJson(configText, NamespaceConfig.class);

            if (!errors.isEmpty())
            {
                break parse;
            }

            namespace.id = supplyId.applyAsInt(namespace.name);

            namespace.readURL = this::readURL;

            // TODO: consider qualified name "namespace::name"
            final NamespaceConfig finalNamespace = namespace;
            namespace.resolveId = name -> name != null ? NamespacedId.id(finalNamespace.id, supplyId.applyAsInt(name)) : 0L;

            for (GuardConfig guard : namespace.guards)
            {
                guard.id = namespace.resolveId.applyAsLong(guard.name);
                guard.readURL = namespace.readURL;
            }

            for (VaultConfig vault : namespace.vaults)
            {
                vault.id = namespace.resolveId.applyAsLong(vault.name);
            }

            for (BindingConfig binding : namespace.bindings)
            {
                binding.id = namespace.resolveId.applyAsLong(binding.entry);
                binding.resolveId = namespace.resolveId;

                if (binding.vault != null)
                {
                    binding.vaultId = namespace.resolveId.applyAsLong(binding.vault);
                }

                for (RouteConfig route : binding.routes)
                {
                    route.id = namespace.resolveId.applyAsLong(route.exit);
                    route.authorized = session -> true;

                    if (route.guarded != null)
                    {
                        for (GuardedConfig guarded : route.guarded)
                        {
                            guarded.id = namespace.resolveId.applyAsLong(guarded.name);

                            LongPredicate authorizer = namespace.guards.stream()
                                .filter(g -> g.id == guarded.id)
                                .findFirst()
                                .map(g -> guardByType.apply(g.type))
                                .map(g -> g.verifier(DispatchAgent::indexOfId, guarded))
                                .orElse(session -> false);

                            LongFunction<String> identifier = namespace.guards.stream()
                                .filter(g -> g.id == guarded.id)
                                .findFirst()
                                .map(g -> guardByType.apply(g.type))
                                .map(g -> g.identifier(DispatchAgent::indexOfId, guarded))
                                .orElse(session -> null);

                            guarded.identity = identifier;

                            route.authorized = route.authorized.and(authorizer);
                        }
                    }
                }

                long affinity = tuning.affinity(binding.id);

                final long maxbits = maxWorkers.apply(binding.type.intern().hashCode()).applyAsInt(binding.kind);
                for (int bitindex = 0; Long.bitCount(affinity) > maxbits; bitindex++)
                {
                    affinity &= ~(1 << bitindex);
                }

                tuning.affinity(binding.id, affinity);
            }
        }
        catch (Throwable ex)
        {
            logError(ex.getMessage());
        }

        if (!errors.isEmpty())
        {
            errors.forEach(this::logError);
        }
        return namespace;
    }

    public void register(
        NamespaceConfig namespace)
    {
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        for (DispatchAgent dispatcher : dispatchers)
        {
            future = CompletableFuture.allOf(future, dispatcher.attach(namespace));
        }
        future.join();
        extensions.forEach(e -> e.onRegistered(context));
    }

    public void unregister(
        NamespaceConfig namespace)
    {
        if (namespace != null)
        {
            CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
            for (DispatchAgent dispatcher : dispatchers)
            {
                future = CompletableFuture.allOf(future, dispatcher.detach(namespace));
            }
            future.join();
            extensions.forEach(e -> e.onUnregistered(context));
        }
    }

    private void logError(
        String message)
    {
        logger.accept("Configuration parsing error: " + message);
    }

    private String readURL(
        URL location)
    {
        String output = null;
        try
        {
            if ("http".equals(location.getProtocol()) || "https".equals(location.getProtocol()))
            {
                HttpClient client = HttpClient.newBuilder()
                    .version(HTTP_2)
                    .followRedirects(NORMAL)
                    .build();

                HttpRequest request = HttpRequest.newBuilder()
                    .GET()
                    .uri(location.toURI())
                    .build();

                HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());

                output = response.body();
            }
            else
            {
                URLConnection connection = location.openConnection();
                try (InputStream input = connection.getInputStream())
                {
                    output = new String(input.readAllBytes(), UTF_8);
                }
            }
        }
        catch (IOException | URISyntaxException | InterruptedException ex)
        {
            rethrowUnchecked(ex);
        }
        return output;
    }

}
