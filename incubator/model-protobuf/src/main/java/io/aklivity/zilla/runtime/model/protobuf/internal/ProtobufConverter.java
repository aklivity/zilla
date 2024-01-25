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
package io.aklivity.zilla.runtime.model.protobuf.internal;

import java.net.URL;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.converter.Converter;
import io.aklivity.zilla.runtime.engine.converter.ConverterContext;

public class ProtobufConverter implements Converter
{
    public static final String NAME = "protobuf";

    @Override
    public String name()
    {
        return NAME;
    }

    @Override
    public ConverterContext supply(
        EngineContext context)
    {
        return new ProtobufConverterContext(context);
    }

    @Override
    public URL type()
    {
        return getClass().getResource("schema/protobuf.schema.patch.json");
    }
}
