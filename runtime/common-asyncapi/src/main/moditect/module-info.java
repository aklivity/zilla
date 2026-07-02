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
module io.aklivity.zilla.runtime.common.asyncapi
{
    requires transitive jakarta.json;
    requires transitive org.agrona;
    requires jakarta.json.bind;
    requires io.aklivity.zilla.runtime.common.json;
    requires io.aklivity.zilla.runtime.common.yaml;

    exports io.aklivity.zilla.runtime.common.asyncapi.config;
    exports io.aklivity.zilla.runtime.common.asyncapi.model;
    exports io.aklivity.zilla.runtime.common.asyncapi.model.bindings;
    exports io.aklivity.zilla.runtime.common.asyncapi.model.bindings.http;
    exports io.aklivity.zilla.runtime.common.asyncapi.model.bindings.http.kafka;
    exports io.aklivity.zilla.runtime.common.asyncapi.model.bindings.kafka;
    exports io.aklivity.zilla.runtime.common.asyncapi.model.bindings.sse;
    exports io.aklivity.zilla.runtime.common.asyncapi.model.bindings.sse.kafka;
    exports io.aklivity.zilla.runtime.common.asyncapi.model.resolver;
    exports io.aklivity.zilla.runtime.common.asyncapi.view;

    opens io.aklivity.zilla.runtime.common.asyncapi.model;
    opens io.aklivity.zilla.runtime.common.asyncapi.model.bindings;
    opens io.aklivity.zilla.runtime.common.asyncapi.model.bindings.http;
    opens io.aklivity.zilla.runtime.common.asyncapi.model.bindings.http.kafka;
    opens io.aklivity.zilla.runtime.common.asyncapi.model.bindings.kafka;
    opens io.aklivity.zilla.runtime.common.asyncapi.model.bindings.sse;
    opens io.aklivity.zilla.runtime.common.asyncapi.model.bindings.sse.kafka;
    opens io.aklivity.zilla.runtime.common.asyncapi.view;
}
