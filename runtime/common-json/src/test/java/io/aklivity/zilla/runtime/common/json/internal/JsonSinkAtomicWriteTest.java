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
package io.aklivity.zilla.runtime.common.json.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

// Exercises JsonSinkImpl directly against its atomic (brace/bracket/boolean/null) events, none of which
// touch the JsonController/JsonSource parameters, so null stand-ins keep these tests focused on the
// generator interaction. Regression coverage for #2040: before atomicPending existed, resume() treated
// every one of these event kinds as already written (inFlight() has no source cursor to check for them),
// so a write that never happened because of a room pre-check would be silently dropped rather than retried.
class JsonSinkAtomicWriteTest
{
    @Test
    void shouldSuspendStartArrayWithoutRoomThenResumeOnceRoomIsAvailable()
    {
        JsonGeneratorImpl generator = new JsonGeneratorImpl();
        JsonSinkImpl sink = new JsonSinkImpl(generator);

        MutableDirectBufferEx tiny = new UnsafeBufferEx(new byte[64]);
        generator.wrap(tiny, 32, 32);
        Status suspended = sink.transform(null, null, JsonEvent.START_ARRAY);

        assertEquals(Status.SUSPENDED, suspended);
        assertEquals(0, generator.length());

        MutableDirectBufferEx roomy = new UnsafeBufferEx(new byte[64]);
        generator.wrap(roomy, 0, roomy.capacity());
        Status resumed = sink.resume(null, null, JsonEvent.START_ARRAY);

        assertEquals(Status.ADVANCED, resumed);
        assertEquals("[", drain(generator, roomy));
    }

    @Test
    void shouldSuspendValueTrueWithoutRoomThenResumeOnceRoomIsAvailable()
    {
        JsonGeneratorImpl generator = new JsonGeneratorImpl();
        JsonSinkImpl sink = new JsonSinkImpl(generator);

        MutableDirectBufferEx tiny = new UnsafeBufferEx(new byte[64]);
        generator.wrap(tiny, 32, 32);
        Status suspended = sink.transform(null, null, JsonEvent.VALUE_TRUE);

        assertEquals(Status.SUSPENDED, suspended);
        assertEquals(0, generator.length());

        MutableDirectBufferEx roomy = new UnsafeBufferEx(new byte[64]);
        generator.wrap(roomy, 0, roomy.capacity());
        Status resumed = sink.resume(null, null, JsonEvent.VALUE_TRUE);

        // a top-level scalar completes the document
        assertEquals(Status.COMPLETED, resumed);
        assertEquals("true", drain(generator, roomy));
    }

    @Test
    void shouldSuspendValueNullWithoutRoomThenResumeOnceRoomIsAvailable()
    {
        JsonGeneratorImpl generator = new JsonGeneratorImpl();
        JsonSinkImpl sink = new JsonSinkImpl(generator);

        MutableDirectBufferEx tiny = new UnsafeBufferEx(new byte[64]);
        generator.wrap(tiny, 32, 32);
        Status suspended = sink.transform(null, null, JsonEvent.VALUE_NULL);

        assertEquals(Status.SUSPENDED, suspended);
        assertEquals(0, generator.length());

        MutableDirectBufferEx roomy = new UnsafeBufferEx(new byte[64]);
        generator.wrap(roomy, 0, roomy.capacity());
        Status resumed = sink.resume(null, null, JsonEvent.VALUE_NULL);

        assertEquals(Status.COMPLETED, resumed);
        assertEquals("null", drain(generator, roomy));
    }

    @Test
    void shouldSuspendEndArrayWithoutRoomThenResumeAndCompleteOnceRoomIsAvailable()
    {
        JsonGeneratorImpl generator = new JsonGeneratorImpl();
        JsonSinkImpl sink = new JsonSinkImpl(generator);

        MutableDirectBufferEx roomy = new UnsafeBufferEx(new byte[64]);
        generator.wrap(roomy, 0, roomy.capacity());
        assertEquals(Status.ADVANCED, sink.transform(null, null, JsonEvent.START_ARRAY));

        MutableDirectBufferEx tiny = new UnsafeBufferEx(new byte[64]);
        generator.wrap(tiny, 32, 32);
        Status suspended = sink.transform(null, null, JsonEvent.END_ARRAY);

        assertEquals(Status.SUSPENDED, suspended);
        assertEquals(0, generator.length());

        MutableDirectBufferEx fresh = new UnsafeBufferEx(new byte[64]);
        generator.wrap(fresh, 0, fresh.capacity());
        Status resumed = sink.resume(null, null, JsonEvent.END_ARRAY);

        // closing the sole open array at depth zero completes the document
        assertEquals(Status.COMPLETED, resumed);
        assertEquals("]", drain(generator, fresh));
    }

    @Test
    void shouldNotRewriteAnAtomicEventThatAlreadySucceeded()
    {
        // a boundary() drain suspends AFTER a successful write; resume() must not repeat it, since
        // atomicPending is only set when the write itself did not happen
        JsonGeneratorImpl generator = new JsonGeneratorImpl();
        JsonSinkImpl sink = new JsonSinkImpl(generator);

        MutableDirectBufferEx output = new UnsafeBufferEx(new byte[64]);
        // remaining() < HEADROOM(16) right after the write triggers boundary()'s post-write suspend
        generator.wrap(output, 0, 1);
        Status suspended = sink.transform(null, null, JsonEvent.START_ARRAY);

        assertEquals(Status.SUSPENDED, suspended);
        assertEquals("[", drain(generator, output));

        MutableDirectBufferEx fresh = new UnsafeBufferEx(new byte[64]);
        generator.wrap(fresh, 0, fresh.capacity());
        Status resumed = sink.resume(null, null, JsonEvent.START_ARRAY);

        // ADVANCED with nothing newly written: the brace from the first call was not duplicated
        assertEquals(Status.ADVANCED, resumed);
        assertEquals(0, generator.length());
    }

    private static String drain(
        JsonGeneratorImpl generator,
        MutableDirectBufferEx buffer)
    {
        byte[] out = new byte[generator.length()];
        buffer.getBytes(0, out);
        return new String(out, UTF_8);
    }
}
