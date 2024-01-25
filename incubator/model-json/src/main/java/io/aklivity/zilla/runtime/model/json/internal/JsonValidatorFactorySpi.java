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
package io.aklivity.zilla.runtime.model.json.internal;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.validator.Validator;
import io.aklivity.zilla.runtime.engine.validator.ValidatorFactorySpi;

public class JsonValidatorFactorySpi implements ValidatorFactorySpi
{
    @Override
    public String type()
    {
        return JsonValidator.NAME;
    }

    @Override
    public Validator create(
        Configuration config)
    {
        return new JsonValidator();
    }
}
