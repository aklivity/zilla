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
module io.aklivity.zilla.runtime.command.generate
{
    requires io.aklivity.zilla.runtime.command;
    requires io.aklivity.zilla.runtime.engine;
    requires io.aklivity.zilla.runtime.binding.http;
    requires io.aklivity.zilla.runtime.binding.mqtt;
    requires io.aklivity.zilla.runtime.binding.tcp;
    requires io.aklivity.zilla.runtime.binding.tls;
    requires io.aklivity.zilla.runtime.catalog.inline;
    requires io.aklivity.zilla.runtime.guard.jwt;
    requires io.aklivity.zilla.runtime.vault.filesystem;
    requires io.aklivity.zilla.runtime.validator.avro;
    requires io.aklivity.zilla.runtime.validator.core;
    requires io.aklivity.zilla.runtime.validator.json;
    requires io.aklivity.zilla.runtime.validator.protobuf;

    requires com.fasterxml.jackson.dataformat.yaml;
    requires com.fasterxml.jackson.databind;

    opens io.aklivity.zilla.runtime.command.generate.internal.airline
        to com.github.rvesse.airline;

    opens io.aklivity.zilla.runtime.command.generate.internal.openapi.model;
    opens io.aklivity.zilla.runtime.command.generate.internal.openapi.view;
    opens io.aklivity.zilla.runtime.command.generate.internal.asyncapi.model;
    opens io.aklivity.zilla.runtime.command.generate.internal.asyncapi.view;

    provides io.aklivity.zilla.runtime.command.ZillaCommandSpi
        with io.aklivity.zilla.runtime.command.generate.internal.ZillaConfigCommandSpi;
}
