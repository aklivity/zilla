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

import static org.junit.Assert.assertEquals;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.openapi.model.Openapi;
import io.aklivity.zilla.runtime.common.openapi.model.OpenapiServer;
import io.aklivity.zilla.runtime.common.openapi.model.OpenapiServerVariable;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiServerView;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiView;

public class OpenapiBindingConfigTest
{
    @Test
    public void shouldWhollyReplaceSingleServerUrlWithOverride()
    {
        OpenapiServer model = new OpenapiServer();
        model.url = "http://localhost:8080/v1";

        Openapi spec = new Openapi();
        spec.servers = List.of(model);

        OpenapiServerView server = OpenapiView.of(spec).servers.get(0);

        assertEquals(URI.create("https://frontend.example.com:443/apis"),
            OpenapiBindingConfig.resolveEffectiveUrl(server, "https://frontend.example.com/apis", true));
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

        assertEquals(URI.create("http://dev.example.com:80"),
            OpenapiBindingConfig.resolveEffectiveUrl(server, "http://dev.example.com", true));
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

        assertEquals(URI.create(override), OpenapiBindingConfig.resolveEffectiveUrl(prod, override, false));
        assertEquals(qa.url, OpenapiBindingConfig.resolveEffectiveUrl(qa, override, false));
    }

    @Test
    public void shouldReturnCanonicalUrlWhenNoOverride()
    {
        OpenapiServer model = new OpenapiServer();
        model.url = "http://localhost:8080/v1";

        Openapi spec = new Openapi();
        spec.servers = List.of(model);

        OpenapiServerView server = OpenapiView.of(spec).servers.get(0);

        assertEquals(server.url, OpenapiBindingConfig.resolveEffectiveUrl(server, null));
        assertEquals(server.url, OpenapiBindingConfig.resolveEffectiveUrl(server, null, true));
    }
}
