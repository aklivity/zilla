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
package io.aklivity.zilla.config.binding.a2a.openapi;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.nullValue;

import java.util.Map;

import org.junit.Test;

import io.aklivity.zilla.config.engine.KindConfig;

public class A2aOpenapiBindingConfigBuilderTest
{
    @Test
    public void shouldBuildProxyBindingWithOptionsAndRoutes()
    {
        A2aOpenapiBindingConfig binding = A2aOpenapiBindingConfig.builder()
            .namespace("test")
            .name("app0")
            .kind(KindConfig.PROXY)
            .options()
                .agent()
                    .name("Petstore")
                    .version("2.0.0")
                    .build()
                .spec()
                    .label("petstore")
                    .server("https://api.petstore.example.com")
                    .catalog()
                        .name("catalog0")
                        .subject("petstore")
                        .version("latest")
                        .build()
                    .security(Map.of("bearerAuth", "authn_jwt"))
                    .build()
                .skill()
                    .id("list_pets")
                    .example("what pets are available")
                    .tag("pets")
                    .build()
                .embedding("embedding0")
                .store("vectors")
                .threshold(0.9)
                .build()
            .route()
                .exit("http0")
                .when()
                    .skill("list_pets")
                    .build()
                .with()
                    .spec("petstore")
                    .operation("listPets")
                    .params(Map.of("limit", "${args.max}"))
                    .build()
                .build()
            .build();

        assertThat(binding.type, equalTo("a2a-openapi"));
        assertThat(binding.kind, equalTo(KindConfig.PROXY));

        A2aOpenapiOptionsConfig options = (A2aOpenapiOptionsConfig) binding.options;
        assertThat(options.agent.name, equalTo("Petstore"));
        assertThat(options.agent.version, equalTo("2.0.0"));
        assertThat(options.agent.description, nullValue());
        assertThat(options.specs, hasSize(1));
        assertThat(options.specs.get(0).label, equalTo("petstore"));
        assertThat(options.specs.get(0).server, equalTo("https://api.petstore.example.com"));
        assertThat(options.specs.get(0).catalogs.get(0).subject, equalTo("petstore"));
        assertThat(options.specs.get(0).security, equalTo(Map.of("bearerAuth", "authn_jwt")));
        assertThat(options.skills, hasSize(1));
        assertThat(options.skills.get(0).id, equalTo("list_pets"));
        assertThat(options.skills.get(0).examples, contains("what pets are available"));
        assertThat(options.skills.get(0).tags, contains("pets"));
        assertThat(options.embedding, equalTo("embedding0"));
        assertThat(options.store, equalTo("vectors"));
        assertThat(options.threshold, equalTo(0.9));

        assertThat(binding.routes, hasSize(1));
        assertThat(binding.routes.get(0).exit, equalTo("http0"));
        assertThat(binding.routes.get(0).when.get(0), instanceOf(A2aOpenapiConditionConfig.class));
        assertThat(((A2aOpenapiConditionConfig) binding.routes.get(0).when.get(0)).skill, equalTo("list_pets"));
        A2aOpenapiWithConfig with = (A2aOpenapiWithConfig) binding.routes.get(0).with;
        assertThat(with.spec, equalTo("petstore"));
        assertThat(with.operation, equalTo("listPets"));
        assertThat(with.params, equalTo(Map.of("limit", "${args.max}")));
    }

    @Test
    public void shouldDefaultThreshold()
    {
        A2aOpenapiOptionsConfig options = A2aOpenapiOptionsConfig.builder()
            .embedding("embedding0")
            .store("store0")
            .build();

        assertThat(options.threshold, equalTo(A2aOpenapiOptionsConfig.THRESHOLD_DEFAULT));
        assertThat(options.skills, hasSize(0));
    }
}
