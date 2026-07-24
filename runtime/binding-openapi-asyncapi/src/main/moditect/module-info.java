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
module io.aklivity.zilla.runtime.binding.openapi.asyncapi
{
    requires io.aklivity.zilla.config.binding.http.kafka;
    requires io.aklivity.zilla.config.binding.openapi.asyncapi;
    requires io.aklivity.zilla.runtime.engine;
    requires io.aklivity.zilla.runtime.common.json;
    requires io.aklivity.zilla.runtime.common.yaml;
    requires io.aklivity.zilla.runtime.common.openapi;
    requires io.aklivity.zilla.runtime.common.asyncapi;
    requires io.aklivity.zilla.runtime.binding.http.kafka;

    provides io.aklivity.zilla.runtime.engine.binding.BindingFactorySpi
        with io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.OpenapiAsyncapiBindingFactorySpi;

    provides io.aklivity.zilla.runtime.engine.event.EventFormatterFactorySpi
        with io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.event.OpenapiAsyncapiEventFormatterFactory;
}
