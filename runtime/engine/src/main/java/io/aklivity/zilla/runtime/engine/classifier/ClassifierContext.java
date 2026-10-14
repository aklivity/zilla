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

import io.aklivity.zilla.config.engine.ClassifierConfig;

/**
 * Per-thread context for a classifier plugin.
 * <p>
 * Created once per I/O thread by {@link Classifier#supply(EngineContext)} and confined to that
 * thread. Manages the lifecycle of {@link ClassifierHandler} instances for classifier
 * configurations active on this thread.
 * </p>
 *
 * @see Classifier
 * @see ClassifierHandler
 */
public interface ClassifierContext
{
    /**
     * Attaches a classifier configuration to this thread's context.
     *
     * @param classifier  the classifier configuration to activate
     * @return a {@link ClassifierHandler} for classifying values,
     *         or {@code null} if this classifier has no handler
     */
    default ClassifierHandler attach(
        ClassifierConfig classifier)
    {
        return null;
    }

    /**
     * Detaches a previously attached classifier configuration, releasing associated resources.
     *
     * @param classifier  the classifier configuration to deactivate
     */
    default void detach(
        ClassifierConfig classifier)
    {
    }
}
