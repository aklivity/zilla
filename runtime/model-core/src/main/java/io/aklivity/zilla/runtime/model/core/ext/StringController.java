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
package io.aklivity.zilla.runtime.model.core.ext;

import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;

/**
 * The per-edge control handle a {@code string} pipeline stage uses to steer its immediate upstream.
 * <p>
 * A mediating {@link StringTransform} supplies its own {@code StringController} to its downstream; a
 * non-mediating stage passes {@code control} through, so a signal raised by any stage reaches the
 * pipeline.
 * </p>
 */
public interface StringController
{
    /**
     * Returns the authorization in effect for the value currently being transformed.
     * <p>
     * A mediating stage may override this to scope the authorization further for a nested composition; a
     * non-mediating stage passes its own through, so this reflects the authorization the pipeline received
     * for the current value unless some stage along the way has narrowed it. The default is {@code 0L},
     * for a pipeline that never received one.
     * </p>
     *
     * @return the authorization in effect for the current value
     */
    default long authorization()
    {
        return 0L;
    }

    /**
     * Returns the metadata travelling alongside the value being transformed, addressed by name rather than
     * by position within the value. The default is {@link ModelEnvelope#NONE}, in force when the caller
     * driving the pipeline supplied no envelope, so a stage reads an empty envelope rather than no
     * envelope at all. A mediating stage may supply its own to its downstream.
     *
     * @return the envelope in force for this pipeline
     */
    default ModelEnvelope envelope()
    {
        return ModelEnvelope.NONE;
    }

    /**
     * Reports {@code sourceBytes} bytes of the current {@link StringEvent#SEGMENT} consumed by a bounded
     * write, so the upstream advances past them and re-exposes the segment's unconsumed remainder when the
     * event resumes. A stage reports this immediately before returning {@link ModelStatus#OVERFLOW} or
     * {@link ModelStatus#SUSPENDED}; on
     * any other status the whole segment is taken as consumed and no report is needed. The default does
     * nothing, for an upstream that forwards no value bytes.
     *
     * @param sourceBytes  the bytes of the current segment consumed by the write that filled the output
     */
    default void consumed(
        int sourceBytes)
    {
    }

    /**
     * Returns the callback a stage invokes when asynchronous work it started completes, after it reported
     * {@link ModelStatus#SUSPENDED}.
     * <p>
     * A stage reports {@link ModelStatus#SUSPENDED} to wait for asynchronous completion, which is distinct
     * from {@link ModelStatus#OVERFLOW} where the output is full and the caller resumes after draining.
     * Invoking the callback tells the caller the pipeline is ready to be driven again; the caller then
     * transforms with an empty source, and the pipeline re-enters the suspended stage through
     * {@link StringTransform#resume(StringController, StringSource, StringEvent, StringSink)} with the event that
     * suspended it re-presented exactly once, together with any bytes of that event the stage has not
     * reported consumed through {@link #consumed(int)}. A suspended pipeline accepts no new input.
     * </p>
     * <p>
     * The callback is invoked on the I/O thread that drives the pipeline, so asynchronous work completes
     * back onto that thread, for example through the engine's signaler, before invoking it. The default is a
     * no-op, for a pipeline driven without a callback.
     * </p>
     *
     * @return the callback to invoke when the asynchronous work completes
     */
    default Runnable resumer()
    {
        return () ->
        {
        };
    }

    /**
     * Rejects the current value, supplying the diagnostic the model reports.
     * <p>
     * A stage raising this also returns {@link ModelStatus#REJECTED}. Rejecting is the outcome for a value
     * a stage found unacceptable: it is reported, with this diagnostic, through the model's own event
     * system. Use {@link #withhold()} instead for the distinct outcome of a stage simply declining to
     * deliver a value it found nothing wrong with.
     * </p>
     *
     * @param diagnostic  the reason the value was rejected
     */
    void reject(
        String diagnostic);

    /**
     * Withholds the current value from delivery.
     * <p>
     * A stage raising this also returns {@link ModelStatus#REJECTED}: the value is not delivered
     * downstream. Unlike {@link #reject(String)} nothing is reported — withholding is a stage's own
     * decision about a value it found nothing wrong with, not a failure, so it raises no event and carries
     * no diagnostic.
     * </p>
     */
    void withhold();
}
