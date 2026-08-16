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
package io.aklivity.zilla.runtime.engine.model;

/**
 * The per-edge control handle a {@link ModelTransform} stage uses to steer the format adapter driving it.
 * <p>
 * A mediating stage supplies its own {@code ModelController} to its downstream; a non-mediating stage
 * passes {@code control} through, so a signal raised by any stage reaches the adapter.
 * </p>
 *
 * @see ModelTransform
 */
public interface ModelController
{
    /**
     * Returns the authorization in effect for the message currently being transformed.
     * <p>
     * A mediating stage may override this to scope the authorization further for a nested composition; a
     * non-mediating stage passes {@code control} through, so this reflects the authorization the pipeline
     * received for the current message unless some stage along the way has narrowed it.
     * </p>
     *
     * @return the authorization in effect for the current message
     */
    long authorization();

    /**
     * Signals that the current value must be rejected, supplying the diagnostic the adapter reports.
     * <p>
     * A stage raising this also returns {@link ModelStatus#REJECTED}; the diagnostic is what distinguishes
     * a rejection with a cause from a bare status. The supplied text is copied out by the adapter before
     * the call returns.
     * </p>
     *
     * @param diagnostic  the reason the value was rejected
     */
    void reject(
        String diagnostic);
}
