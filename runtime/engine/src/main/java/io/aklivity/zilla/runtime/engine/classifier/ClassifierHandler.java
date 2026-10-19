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

import java.util.List;

/**
 * Reports labels present in values, for a specific classifier configuration.
 * <p>
 * A {@code ClassifierHandler} is obtained from {@link ClassifierContext#attach(ClassifierConfig)}
 * and is confined to a single I/O thread.
 * </p>
 * <p>
 * Capability is whether the corresponding object can be obtained: a method returning
 * {@code null} means the classifier cannot provide it for the requested labels. This is
 * resolved once when the caller binds, rather than on every call.
 * </p>
 * <p>
 * Labels are the aliases declared by the classifier configuration. Each method rejects an
 * unrecognized alias with an {@link IllegalArgumentException}.
 * </p>
 *
 * @see Detector
 * @see Anonymizer
 * @see Deanonymizer
 */
public interface ClassifierHandler
{
    /**
     * Creates a {@link Detector} that reports whether any of the labels is present in a value.
     * Always available.
     *
     * @param labels  the label aliases to detect
     * @return a new {@link Detector}
     */
    Detector initDetector(
        List<String> labels);

    /**
     * Creates an {@link Anonymizer} that substitutes located labels with surrogates.
     *
     * @param labels  the label aliases to substitute
     * @return a new {@link Anonymizer}, or {@code null} if any label cannot be located
     */
    default Anonymizer initAnonymizer(
        List<String> labels)
    {
        return null;
    }

    /**
     * Creates a {@link Deanonymizer} that restores surrogates minted by an {@link Anonymizer}
     * of this classifier.
     *
     * @param labels  the label aliases to restore
     * @return a new {@link Deanonymizer}, or {@code null} if this classifier cannot restore
     */
    default Deanonymizer initDeanonymizer(
        List<String> labels)
    {
        return null;
    }
}
