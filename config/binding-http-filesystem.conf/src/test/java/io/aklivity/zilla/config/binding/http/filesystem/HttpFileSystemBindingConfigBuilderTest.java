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
package io.aklivity.zilla.config.binding.http.filesystem;

import static io.aklivity.zilla.config.engine.KindConfig.SERVER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;

import org.junit.Test;

import io.aklivity.zilla.config.binding.http.filesystem.internal.HttpFileSystemBindingInfo;
import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.RouteConfig;

public class HttpFileSystemBindingConfigBuilderTest
{
    @Test
    public void shouldBuildBindingViaFluentKindSpecificChain()
    {
        HttpFileSystemBindingConfig binding = HttpFileSystemBindingConfig.builder()
            .namespace("test")
            .name("http-filesystem0")
            .kind(SERVER)
            .route()
                .when()
                    .path("/files/{path}")
                    .method("GET")
                    .build()
                .with()
                    .directory("/var/www")
                    .path("{path}")
                    .build()
                .build()
            .build();

        assertThat(binding.namespace, equalTo("test"));
        assertThat(binding.name, equalTo("http-filesystem0"));
        assertThat(binding.type, equalTo(HttpFileSystemBindingInfo.TYPE));
        assertThat(binding.kind, equalTo(SERVER));

        assertThat(binding.routes, hasSize(1));

        RouteConfig route = binding.routes.get(0);
        assertThat(route.when, hasSize(1));

        ConditionConfig condition = route.when.get(0);
        assertThat(condition, instanceOf(HttpFileSystemConditionConfig.class));
        assertThat(((HttpFileSystemConditionConfig) condition).path, equalTo("/files/{path}"));
        assertThat(((HttpFileSystemConditionConfig) condition).method, equalTo("GET"));

        assertThat(route.with, instanceOf(HttpFileSystemWithConfig.class));
        assertThat(((HttpFileSystemWithConfig) route.with).directory, equalTo("/var/www"));
        assertThat(((HttpFileSystemWithConfig) route.with).path, equalTo("{path}"));
    }
}
