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
package io.aklivity.zilla.runtime.classifier.presidio.internal;

import java.util.HashMap;
import java.util.Map;

import io.aklivity.zilla.config.engine.ClassifierConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.classifier.ClassifierContext;
import io.aklivity.zilla.runtime.engine.classifier.ClassifierHandler;

final class PresidioClassifierContext implements ClassifierContext
{
    private final EngineContext context;
    private final Map<String, PresidioClassifierHandler> handlers;

    PresidioClassifierContext(
        EngineContext context)
    {
        this.context = context;
        this.handlers = new HashMap<>();
    }

    @Override
    public ClassifierHandler attach(
        ClassifierConfig classifier)
    {
        PresidioClassifierHandler handler = new PresidioClassifierHandler(context, classifier);
        handlers.put(classifier.qname, handler);
        return handler;
    }

    @Override
    public void detach(
        ClassifierConfig classifier)
    {
        PresidioClassifierHandler handler = handlers.remove(classifier.qname);

        if (handler != null)
        {
            handler.close();
        }
    }
}
