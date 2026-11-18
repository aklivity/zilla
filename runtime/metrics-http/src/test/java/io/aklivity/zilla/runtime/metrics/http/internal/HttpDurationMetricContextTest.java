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
package io.aklivity.zilla.runtime.metrics.http.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.engine.AttributeConfig;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.HttpResetExFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.ResetFW;

public class HttpDurationMetricContextTest
{
    private static final long INITIAL_ID = 3L;
    private static final long REPLY_ID = 2L;

    private final MutableDirectBufferEx frameBuffer = new UnsafeBufferEx(new byte[1024]);
    private final MutableDirectBufferEx extensionBuffer = new UnsafeBufferEx(new byte[256]);
    private final List<String> labels = new ArrayList<>();
    private final List<String> recordedLabels = new ArrayList<>();
    private final List<Long> recordedDurations = new ArrayList<>();

    private MessageConsumer handler;

    @Before
    public void init()
    {
        HttpDurationMetricContext context = new HttpDurationMetricContext("http", Metric.Kind.HISTOGRAM, label ->
        {
            labels.add(label);
            return labels.size();
        });
        List<AttributeConfig> attributes = List.of(
            AttributeConfig.builder().name("method").value("${http.request.method}").build(),
            AttributeConfig.builder().name("status").value("${http.response.status}").build());
        handler = context.supply(attributesId -> duration ->
        {
            recordedLabels.add(attributesId == 0 ? "" : labels.get(attributesId - 1));
            recordedDurations.add(duration);
        }, attributes);
    }

    @Test
    public void shouldRecordCompletedExchange()
    {
        begin(INITIAL_ID, 1_000L, ":method", "GET");
        begin(REPLY_ID, 2_000L, ":status", "200");
        end(INITIAL_ID, 2_500L);
        end(REPLY_ID, 3_000L);

        assertThat(recordedLabels, equalTo(List.of("method=GET,status=200")));
        assertThat(recordedDurations, equalTo(List.of(2_000L)));
    }

    @Test
    public void shouldRecordRequestRejectedWithStatusFromResetExtension()
    {
        begin(INITIAL_ID, 1_000L, ":method", "POST");
        end(INITIAL_ID, 1_200L);
        reset(INITIAL_ID, 5_000L, ":status", "401");

        assertThat(recordedLabels, equalTo(List.of("method=POST,status=401")));
        assertThat(recordedDurations, equalTo(List.of(4_000L)));
    }

    @Test
    public void shouldRecordRequestRejectedWithoutStatusAsNotFound()
    {
        begin(INITIAL_ID, 1_000L, ":method", "GET");
        end(INITIAL_ID, 1_000L);
        reset(INITIAL_ID, 1_500L, null, null);

        assertThat(recordedLabels, equalTo(List.of("method=GET,status=404")));
        assertThat(recordedDurations, equalTo(List.of(500L)));
    }

    @Test
    public void shouldRecordResponseWhenRequestResetAfterResponseBegins()
    {
        begin(INITIAL_ID, 1_000L, ":method", "POST");
        begin(REPLY_ID, 2_000L, ":status", "413");
        reset(INITIAL_ID, 2_100L, null, null);
        end(REPLY_ID, 3_000L);

        assertThat(recordedLabels, equalTo(List.of("method=POST,status=413")));
        assertThat(recordedDurations, equalTo(List.of(2_000L)));
    }

    @Test
    public void shouldNotRecordRequestResetBeforeItEnds()
    {
        begin(INITIAL_ID, 1_000L, ":method", "POST");
        reset(INITIAL_ID, 1_500L, null, null);

        assertThat(recordedDurations, equalTo(List.of()));
    }

    @Test
    public void shouldNotRecordRequestAbortedByClient()
    {
        begin(INITIAL_ID, 1_000L, ":method", "GET");
        abort(INITIAL_ID, 2_000L);

        assertThat(recordedDurations, equalTo(List.of()));
    }

    private void begin(
        long streamId,
        long timestamp,
        String name,
        String value)
    {
        HttpBeginExFW extension = new HttpBeginExFW.Builder()
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .typeId(0)
            .headersItem(h -> h.name(name).value(value))
            .build();
        BeginFW begin = new BeginFW.Builder()
            .wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0L)
            .routedId(0L)
            .streamId(streamId)
            .sequence(0L)
            .acknowledge(0L)
            .state(0)
            .maximum(0)
            .timestamp(timestamp)
            .traceId(0L)
            .authorization(0L)
            .affinity(0L)
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();
        handler.accept(BeginFW.TYPE_ID, begin.buffer(), begin.offset(), begin.sizeof());
    }

    private void reset(
        long streamId,
        long timestamp,
        String name,
        String value)
    {
        ResetFW.Builder builder = new ResetFW.Builder()
            .wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0L)
            .routedId(0L)
            .streamId(streamId)
            .sequence(0L)
            .acknowledge(0L)
            .state(0)
            .maximum(0)
            .timestamp(timestamp)
            .traceId(0L)
            .authorization(0L);
        ResetFW reset;
        if (name != null)
        {
            HttpResetExFW extension = new HttpResetExFW.Builder()
                .wrap(extensionBuffer, 0, extensionBuffer.capacity())
                .typeId(0)
                .headersItem(h -> h.name(name).value(value))
                .build();
            reset = builder.extension(extension.buffer(), extension.offset(), extension.sizeof()).build();
        }
        else
        {
            reset = builder.extension(extensionBuffer, 0, 0).build();
        }
        handler.accept(ResetFW.TYPE_ID, reset.buffer(), reset.offset(), reset.sizeof());
    }

    private void end(
        long streamId,
        long timestamp)
    {
        EndFW end = new EndFW.Builder()
            .wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0L)
            .routedId(0L)
            .streamId(streamId)
            .sequence(0L)
            .acknowledge(0L)
            .state(0)
            .maximum(0)
            .timestamp(timestamp)
            .traceId(0L)
            .authorization(0L)
            .extension(extensionBuffer, 0, 0)
            .build();
        handler.accept(EndFW.TYPE_ID, end.buffer(), end.offset(), end.sizeof());
    }

    private void abort(
        long streamId,
        long timestamp)
    {
        AbortFW abort = new AbortFW.Builder()
            .wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(0L)
            .routedId(0L)
            .streamId(streamId)
            .sequence(0L)
            .acknowledge(0L)
            .state(0)
            .maximum(0)
            .timestamp(timestamp)
            .traceId(0L)
            .authorization(0L)
            .extension(extensionBuffer, 0, 0)
            .build();
        handler.accept(AbortFW.TYPE_ID, abort.buffer(), abort.offset(), abort.sizeof());
    }
}
