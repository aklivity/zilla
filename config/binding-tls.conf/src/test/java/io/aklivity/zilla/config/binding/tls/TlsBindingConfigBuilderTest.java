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
package io.aklivity.zilla.config.binding.tls;

import static io.aklivity.zilla.config.engine.KindConfig.SERVER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;

import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.RouteConfig;

public class TlsBindingConfigBuilderTest
{
    @Test
    public void shouldBuildBindingViaFluentKindSpecificChain()
    {
        TlsBindingConfig binding = TlsBindingConfig.builder()
            .namespace("test")
            .name("tls0")
            .kind(SERVER)
            .options()
                .sni(List.of("example.com"))
                .build()
            .route()
                .when()
                    .authority("example.com")
                    .build()
                .build()
            .build();

        assertThat(binding.namespace, equalTo("test"));
        assertThat(binding.name, equalTo("tls0"));
        assertThat(binding.type, equalTo(TlsBindingInfo.TYPE));
        assertThat(binding.kind, equalTo(SERVER));

        TlsOptionsConfig options = (TlsOptionsConfig) binding.options;
        assertThat(options.sni, equalTo(List.of("example.com")));

        assertThat(binding.routes, hasSize(1));

        RouteConfig route = binding.routes.get(0);
        assertThat(route.when, hasSize(1));

        ConditionConfig condition = route.when.get(0);
        assertThat(condition, instanceOf(TlsConditionConfig.class));
        assertThat(((TlsConditionConfig) condition).authority, equalTo("example.com"));
    }
}
