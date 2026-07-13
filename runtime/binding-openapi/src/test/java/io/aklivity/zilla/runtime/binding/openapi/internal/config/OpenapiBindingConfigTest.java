/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.openapi.internal.config;

import static io.aklivity.zilla.runtime.engine.config.KindConfig.SERVER;
import static org.junit.Assert.assertEquals;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiCatalogConfig;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiSpecificationConfig;
import io.aklivity.zilla.runtime.common.openapi.model.Openapi;
import io.aklivity.zilla.runtime.common.openapi.model.OpenapiServer;
import io.aklivity.zilla.runtime.common.openapi.model.OpenapiServerVariable;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiServerView;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiView;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public class OpenapiBindingConfigTest
{
    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private EngineContext context;

    private OpenapiBindingConfig newBindingConfig(
        String override)
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("composite0")
            .type("openapi")
            .kind(SERVER)
            .options(OpenapiOptionsConfig.builder()
                .spec(new OpenapiSpecificationConfig(
                    "petstore",
                    override,
                    List.of(new OpenapiCatalogConfig("catalog0", "test", "latest")),
                    null))
                .build())
            .exit("openapi0")
            .build();
        binding.resolveId = name -> 1L;

        return new OpenapiBindingConfig(
            context, binding, new HttpBeginExFW(), new HttpBeginExFW.Builder(),
            new UnsafeBufferEx(new byte[1024]), 0);
    }

    private static OpenapiServerView server(
        String url)
    {
        OpenapiServer model = new OpenapiServer();
        model.url = url;

        Openapi spec = new Openapi();
        spec.servers = List.of(model);

        return OpenapiView.of(spec).servers.get(0);
    }

    @Test
    public void shouldWhollyReplaceSingleServerUrlWithOverride()
    {
        OpenapiServerView server = server("http://localhost:8080/v1");
        OpenapiBindingConfig binding = newBindingConfig("https://frontend.example.com/apis");

        assertEquals(URI.create("https://frontend.example.com:443/apis"),
            binding.resolveServer(server, "petstore", true));
    }

    @Test
    public void shouldAcceptSingleServerUrlOverrideRegardlessOfVariablePattern()
    {
        OpenapiServerVariable variable = new OpenapiServerVariable();
        variable.values = List.of("prod", "staging");
        variable.defaultValue = "prod";

        OpenapiServer model = new OpenapiServer();
        model.url = "http://{env}.example.com";
        model.variables = Map.of("env", variable);

        Openapi spec = new Openapi();
        spec.servers = List.of(model);

        OpenapiServerView server = OpenapiView.of(spec).servers.get(0);
        OpenapiBindingConfig binding = newBindingConfig("http://dev.example.com");

        assertEquals(URI.create("http://dev.example.com:80"), binding.resolveServer(server, "petstore", true));
    }

    @Test
    public void shouldWhollyReplaceOnlyTheMatchingServerAmongMultipleDeclaredServers()
    {
        OpenapiServer prodModel = new OpenapiServer();
        prodModel.url = "http://localhost:9090/prod";

        OpenapiServer qaModel = new OpenapiServer();
        qaModel.url = "http://localhost:8080/qa";

        Openapi spec = new Openapi();
        spec.servers = List.of(prodModel, qaModel);

        OpenapiView view = OpenapiView.of(spec);
        OpenapiServerView prod = view.servers.get(0);
        OpenapiServerView qa = view.servers.get(1);

        final String override = "http://localhost:9090/prod";
        OpenapiBindingConfig binding = newBindingConfig(override);

        assertEquals(URI.create(override), binding.resolveServer(prod, "petstore", false));
        assertEquals(qa.url, binding.resolveServer(qa, "petstore", false));
    }

    @Test
    public void shouldReturnCanonicalUrlWhenNoOverride()
    {
        OpenapiServerView server = server("http://localhost:8080/v1");
        OpenapiBindingConfig binding = newBindingConfig(null);

        assertEquals(server.url, binding.resolveServer(server, "petstore", true));
        assertEquals(server.url, binding.resolveServer(server, "petstore"));
    }

    @Test
    public void shouldReturnCanonicalUrlWhenSpecLabelDoesNotMatch()
    {
        OpenapiServerView server = server("http://localhost:8080/v1");
        OpenapiBindingConfig binding = newBindingConfig("https://frontend.example.com/apis");

        assertEquals(server.url, binding.resolveServer(server, "other-spec", true));
    }
}
