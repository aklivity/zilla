/*
 * Copyright 2021-2026 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.classifier;

import io.aklivity.zilla.config.engine.factory.Aliasable;
import io.aklivity.zilla.runtime.engine.EngineContext;

/**
 * Entry point for a classifier plugin.
 * <p>
 * A {@code Classifier} reports which labels are present in a value. It is a detector: it
 * never returns rewritten content. Implementations are technique- or vendor-specific
 * (e.g. pattern matching, semantic similarity, or a remote analysis service).
 * </p>
 * <p>
 * Implementations are discovered via {@link java.util.ServiceLoader} through
 * {@link ClassifierFactorySpi}. A classifier may declare aliases via {@link Aliasable#aliases()}
 * to support multiple configuration names.
 * </p>
 *
 * @see ClassifierContext
 * @see ClassifierHandler
 * @see ClassifierFactorySpi
 */
public interface Classifier extends Aliasable
{
    /**
     * Returns the unique name identifying this classifier type, e.g. {@code "patterns"}.
     *
     * @return the classifier type name
     */
    String name();

    /**
     * Creates a per-thread context for this classifier.
     *
     * @param context  the engine context for the calling I/O thread
     * @return a new {@link ClassifierContext} confined to that thread
     */
    ClassifierContext supply(
        EngineContext context);
}
