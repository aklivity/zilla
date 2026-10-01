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
package io.aklivity.zilla.runtime.binding.llm.internal.config;

import java.util.List;
import java.util.regex.Pattern;

import io.aklivity.zilla.config.binding.llm.LlmConditionConfig;
import io.aklivity.zilla.runtime.common.lang.Matchers;

final class LlmConditionMatcher
{
    private final String dialect;
    private final List<Pattern> modelAllow;

    LlmConditionMatcher(
        LlmConditionConfig condition)
    {
        this.dialect = condition.dialect;
        this.modelAllow = Matchers.globAll(condition.model);
    }

    boolean matches(
        String dialect,
        String model)
    {
        return matchesDialect(dialect) && matchesModel(model);
    }

    private boolean matchesDialect(
        String dialect)
    {
        return this.dialect == null || this.dialect.equals(dialect);
    }

    private boolean matchesModel(
        String model)
    {
        return modelAllow == null || model != null && Matchers.admits(modelAllow, model);
    }
}
