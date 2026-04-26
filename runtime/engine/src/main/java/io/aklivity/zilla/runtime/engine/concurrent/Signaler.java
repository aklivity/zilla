/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.concurrent;

import java.util.function.IntConsumer;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

/**
 * Schedules time-based and task-based signals on the owning I/O thread.
 * <p>
 * A {@code Signaler} delivers signals to stream handlers without requiring threads to block
 * or poll. Signals are delivered as synthetic frames on the stream identified by
 * {@code (originId, routedId, streamId)}, allowing a handler to process timer events and
 * deferred tasks through the same {@link MessageConsumer} dispatch path used for normal frames.
 * </p>
 * <p>
 * All signal delivery happens on the owning I/O thread. Calls to schedule signals may
 * be made from any thread, but cancellation ({@link #cancel}) must be called from the
 * owning I/O thread.
 * </p>
 *
 * @see EngineContext#signaler()
 */
public interface Signaler
{
    /**
     * Sentinel cancel id returned by scheduling methods when the signal cannot be cancelled
     * (e.g., already fired or not cancellable).
     */
    long NO_CANCEL_ID = 0xffff_ffff_ffff_ffffL;

    /**
     * Schedules a lightweight timer signal to fire at the given wall-clock time, delivering
     * it to a simple {@link IntConsumer} handler rather than a stream.
     * <p>
     * Intended for internal engine timers that do not need stream context.
     * </p>
     *
     * @param timeMillis  the UTC millisecond time at which to fire the signal
     * @param signalId    an application-defined signal identifier passed to {@code handler}
     * @param handler     the callback to invoke with {@code signalId} when the timer fires
     * @return a cancel id that can be passed to {@link #cancel}, or {@link #NO_CANCEL_ID}
     */
    long signalAt(long timeMillis, int signalId, IntConsumer handler);

    /**
     * Immediately delivers a signal to the stream identified by
     * {@code (originId, routedId, streamId)} as if a synthetic frame had arrived.
     *
     * @param originId   the origin binding id of the target stream
     * @param routedId   the routed binding id of the target stream
     * @param streamId   the stream id of the target stream
     * @param traceId    the trace identifier for diagnostics
     * @param signalId   an application-defined signal identifier
     * @param contextId  an application-defined context value carried with the signal
     */
    void signalNow(long originId, long routedId, long streamId, long traceId, int signalId, int contextId);

    /**
     * Immediately delivers a signal with an attached data payload to the target stream.
     *
     * @param originId   the origin binding id
     * @param routedId   the routed binding id
     * @param streamId   the stream id
     * @param traceId    the trace identifier
     * @param signalId   an application-defined signal identifier
     * @param contextId  an application-defined context value
     * @param buffer     the buffer containing additional signal data
     * @param offset     the offset of the data in the buffer
     * @param length     the length of the data
     */
    void signalNow(long originId, long routedId, long streamId, long traceId, int signalId, int contextId,
                   DirectBufferEx buffer, int offset, int length);

    /**
     * Schedules a signal to be delivered to the target stream at the given wall-clock time.
     *
     * @param timeMillis  the UTC millisecond time at which to deliver the signal
     * @param originId    the origin binding id
     * @param routedId    the routed binding id
     * @param streamId    the stream id
     * @param traceId     the trace identifier
     * @param signalId    an application-defined signal identifier
     * @param contextId   an application-defined context value
     * @return a cancel id that can be passed to {@link #cancel}, or {@link #NO_CANCEL_ID}
     */
    long signalAt(long timeMillis, long originId, long routedId, long streamId, long traceId, int signalId, int contextId);

    /**
     * Schedules a {@link Runnable} task to run on the owning I/O thread, delivered as a
     * signal to the target stream after the task has been queued.
     *
     * @param task       the task to execute on the I/O thread
     * @param originId   the origin binding id
     * @param routedId   the routed binding id
     * @param streamId   the stream id to signal after the task is queued
     * @param traceId    the trace identifier
     * @param signalId   an application-defined signal identifier
     * @param contextId  an application-defined context value
     * @return a cancel id that can be passed to {@link #cancel}, or {@link #NO_CANCEL_ID}
     */
    long signalTask(Runnable task, long originId, long routedId, long streamId, long traceId, int signalId, int contextId);

    /**
     * Cancels a previously scheduled signal or task.
     * <p>
     * Must be called from the owning I/O thread. Has no effect if the signal has already fired
     * or the cancel id is {@link #NO_CANCEL_ID}.
     * </p>
     *
     * @param cancelId  the cancel id returned by a prior scheduling call
     * @return {@code true} if the signal was successfully cancelled before firing,
     *         {@code false} if it had already fired or could not be cancelled
     */
    boolean cancel(long cancelId);
}
