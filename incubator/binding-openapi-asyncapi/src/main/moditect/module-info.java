/*
 * Copyright 2021-2023 Aklivity Inc
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
    requires org.leadpony.justify;

    requires io.aklivity.zilla.runtime.engine;
    requires io.aklivity.zilla.runtime.binding.asyncapi;
    requires io.aklivity.zilla.runtime.binding.openapi;
    requires io.aklivity.zilla.runtime.binding.http;
    requires io.aklivity.zilla.runtime.binding.kafka;
    requires io.aklivity.zilla.runtime.binding.http.kafka;

    exports io.aklivity.zilla.runtime.binding.openapi.asyncapi.config;

    provides io.aklivity.zilla.runtime.engine.binding.BindingFactorySpi
        with io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.OpenapiAsyncapiBindingFactorySpi;

    provides io.aklivity.zilla.runtime.engine.config.CompositeBindingAdapterSpi
        with io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.config.OpenapiAsyncCompositeBindingAdapter;

    provides io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi
        with io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.config.OpenapiAsyncapiOptionsConfigAdapter;

    provides io.aklivity.zilla.runtime.engine.config.ConditionConfigAdapterSpi
        with io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.config.OpenapiAsyncapiConditionConfigAdapter;

    provides io.aklivity.zilla.runtime.engine.config.WithConfigAdapterSpi
        with io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.config.OpenapiAsyncapiWithConfigAdapter;
}
