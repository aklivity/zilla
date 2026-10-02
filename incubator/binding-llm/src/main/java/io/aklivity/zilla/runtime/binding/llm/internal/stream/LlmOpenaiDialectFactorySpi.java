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
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialect;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialectFactorySpi;
import io.aklivity.zilla.runtime.binding.llm.internal.LlmConfiguration;
import io.aklivity.zilla.runtime.engine.Configuration;

public final class LlmOpenaiDialectFactorySpi implements LlmDialectFactorySpi
{
    static final String NAME = "openai";

    @Override
    public String name()
    {
        return NAME;
    }

    @Override
    public LlmDialect create(
        Configuration config)
    {
        return new LlmOpenaiDialect(new LlmConfiguration(config));
    }
}
