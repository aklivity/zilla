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
package io.aklivity.zilla.runtime.binding.llm.internal.config;

import static java.util.stream.Collectors.toList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

import io.aklivity.zilla.config.binding.llm.LlmOptionsConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.CatalogedConfig;
import io.aklivity.zilla.config.engine.KindConfig;
import io.aklivity.zilla.config.model.json.JsonModelConfig;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialect;
import io.aklivity.zilla.runtime.binding.llm.internal.dialect.LlmDialectResolver;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;

public final class LlmBindingConfig
{
    // the shared inline catalog LlmSystemNamespaceGenerator installs into the sys: namespace, keyed by
    // "<dialect>.request" / "<dialect>.response" subjects -- same catalog id for every dialect, only the
    // schema subject varies, so it is resolved once per binding rather than once per dialect
    private static final String SYSTEM_CATALOG_NAME = "sys:llm_dialects";
    private static final String SUBJECT_REQUEST_SUFFIX = ".request";
    private static final String SCHEMA_VERSION_LATEST = "latest";

    private static final LlmOptionsConfig DEFAULT_OPTIONS = LlmOptionsConfig.builder().build();

    public final long id;
    public final String name;
    public final KindConfig kind;
    public final LlmOptionsConfig options;
    public final List<LlmRouteConfig> routes;

    private final LlmDialectResolver dialects;
    private final EngineContext context;
    private final ToLongFunction<String> resolveId;
    private final Map<String, ModelHandler> modelsByDialect;

    private long catalogId = -1L;

    public LlmBindingConfig(
        BindingConfig binding,
        EngineContext context)
    {
        this.id = binding.id;
        this.name = binding.name;
        this.kind = binding.kind;
        this.options = binding.options instanceof LlmOptionsConfig o ? o : DEFAULT_OPTIONS;
        this.routes = binding.routes.stream().map(LlmRouteConfig::new).collect(toList());
        this.dialects = new LlmDialectResolver(this.options.dialect);
        this.context = context;
        this.resolveId = binding.resolveId;
        this.modelsByDialect = new HashMap<>();
    }

    public LlmRouteConfig resolve(
        long authorization)
    {
        LlmRouteConfig resolved = null;
        for (LlmRouteConfig route : routes)
        {
            if (route.authorized(authorization))
            {
                resolved = route;
                break;
            }
        }
        return resolved;
    }

    public LlmDialect resolveDialect(
        ModelEnvelope headers)
    {
        return dialects.resolve(headers);
    }

    public ModelHandler supplyModel(
        LlmDialect dialect)
    {
        ModelHandler model = modelsByDialect.get(dialect.name());
        if (model == null)
        {
            model = context.supplyModel(newModelConfig(dialect));
            modelsByDialect.put(dialect.name(), model);
        }
        return model;
    }

    private JsonModelConfig newModelConfig(
        LlmDialect dialect)
    {
        CatalogedConfig cataloged = CatalogedConfig.builder()
            .name(SYSTEM_CATALOG_NAME)
            .schema()
                .subject(dialect.name() + SUBJECT_REQUEST_SUFFIX)
                .version(SCHEMA_VERSION_LATEST)
                .build()
            .build();
        cataloged.id = resolveCatalogId();

        return JsonModelConfig.builder()
            .catalog(cataloged)
            .build();
    }

    private long resolveCatalogId()
    {
        if (catalogId == -1L)
        {
            catalogId = resolveId.applyAsLong(SYSTEM_CATALOG_NAME);
        }
        return catalogId;
    }
}
