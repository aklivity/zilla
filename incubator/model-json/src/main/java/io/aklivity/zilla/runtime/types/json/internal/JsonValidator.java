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
package io.aklivity.zilla.runtime.types.json.internal;

import java.net.URL;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.validator.Validator;
import io.aklivity.zilla.runtime.engine.validator.ValidatorContext;

public class JsonValidator implements Validator
{
    public static final String NAME = "json";

    @Override
    public String name()
    {
        return NAME;
    }

    @Override
    public ValidatorContext supply(
        EngineContext context)
    {
        return new JsonValidatorContext(context);
    }

    @Override
    public URL type()
    {
        return getClass().getResource("schema/json.schema.patch.json");
    }
}
