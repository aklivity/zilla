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
package io.aklivity.zilla.runtime.classifier.patterns.internal;

import io.aklivity.zilla.config.engine.ClassifierConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.classifier.ClassifierContext;
import io.aklivity.zilla.runtime.engine.classifier.ClassifierHandler;

final class PatternsClassifierContext implements ClassifierContext
{
    private final EngineContext context;
    private final PatternsClassifierConfiguration configuration;

    PatternsClassifierContext(
        EngineContext context,
        PatternsClassifierConfiguration configuration)
    {
        this.context = context;
        this.configuration = configuration;
    }

    @Override
    public ClassifierHandler attach(
        ClassifierConfig classifier)
    {
        return new PatternsClassifierHandler(context, classifier, configuration);
    }
}
