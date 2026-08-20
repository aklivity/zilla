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
package io.aklivity.zilla.runtime.binding.kafka.internal.cache;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;

public class KafkaTransformTest
{
    private final List<String> events = new ArrayList<>();
    private final Content content = new Content();

    private final KafkaSink recorder = (control, source, event) ->
    {
        events.add(describe(source, event));
        return ModelStatus.OK;
    };

    private final KafkaController control = diagnostic ->
    {
    };

    @Test
    public void shouldForwardEveryEventWhenNone()
    {
        drive(KafkaTransform.NONE, KafkaEvent.SWITCH_VALUE, null, null);
        drive(KafkaTransform.NONE, KafkaEvent.FIELD, "$.id", "one");

        assertEquals(List.of("SWITCH_VALUE", "FIELD($.id=one)"), events);
    }

    @Test
    public void shouldComposeAwayNone()
    {
        KafkaTransform stage = new KafkaExtractTransform(
            KafkaEvent.SWITCH_VALUE, KafkaEvent.SWITCH_HEADERS, "$.id", "id");

        assertSame(stage, stage.andThen(KafkaTransform.NONE));
        assertSame(stage, KafkaTransform.NONE.andThen(stage));
    }

    @Test
    public void shouldAppendToTargetLaneAndSwitchBack()
    {
        KafkaTransform stage = new KafkaExtractTransform(
            KafkaEvent.SWITCH_VALUE, KafkaEvent.SWITCH_HEADERS, "$.region", "region");

        drive(stage, KafkaEvent.SWITCH_VALUE, null, null);
        drive(stage, KafkaEvent.FIELD, "$.region", "east");

        // the match is appended to the headers lane the instant it is found, the value lane is reselected,
        // and the matched field still reaches its own destination untouched
        assertEquals(List.of("SWITCH_VALUE", "SWITCH_HEADERS", "FIELD(region=east)", "SWITCH_VALUE",
            "FIELD($.region=east)"), events);
    }

    @Test
    public void shouldIgnoreMatchingPathInAnotherLane()
    {
        KafkaTransform stage = new KafkaExtractTransform(
            KafkaEvent.SWITCH_VALUE, KafkaEvent.SWITCH_HEADERS, "$.region", "region");

        drive(stage, KafkaEvent.SWITCH_KEY, null, null);
        drive(stage, KafkaEvent.FIELD, "$.region", "east");

        assertEquals(List.of("SWITCH_KEY", "FIELD($.region=east)"), events);
    }

    @Test
    public void shouldIgnoreUnmatchedPath()
    {
        KafkaTransform stage = new KafkaExtractTransform(
            KafkaEvent.SWITCH_VALUE, KafkaEvent.SWITCH_HEADERS, "$.region", "region");

        drive(stage, KafkaEvent.SWITCH_VALUE, null, null);
        drive(stage, KafkaEvent.FIELD, "$.status", "ok");

        assertEquals(List.of("SWITCH_VALUE", "FIELD($.status=ok)"), events);
    }

    @Test
    public void shouldAppendOnceForEachComposedStage()
    {
        KafkaTransform stages = new KafkaExtractTransform(
                KafkaEvent.SWITCH_VALUE, KafkaEvent.SWITCH_HEADERS, "$.region", "region")
            .andThen(new KafkaExtractTransform(
                KafkaEvent.SWITCH_VALUE, KafkaEvent.SWITCH_HEADERS, "$.region", "zone"));

        drive(stages, KafkaEvent.SWITCH_VALUE, null, null);
        drive(stages, KafkaEvent.FIELD, "$.region", "east");

        // the second stage tracks the lane switches the first stage raises, so it still matches the field
        // when it arrives, and both headers reach the terminal
        assertEquals(List.of("SWITCH_VALUE", "SWITCH_HEADERS", "FIELD(region=east)", "SWITCH_VALUE",
            "SWITCH_HEADERS", "FIELD(zone=east)", "SWITCH_VALUE", "FIELD($.region=east)"), events);
    }

    @Test
    public void shouldForgetLaneOnReset()
    {
        KafkaTransform stages = new KafkaExtractTransform(
                KafkaEvent.SWITCH_VALUE, KafkaEvent.SWITCH_HEADERS, "$.region", "region")
            .andThen(new KafkaExtractTransform(
                KafkaEvent.SWITCH_VALUE, KafkaEvent.SWITCH_HEADERS, "$.status", "status"));

        drive(stages, KafkaEvent.SWITCH_VALUE, null, null);
        stages.reset();
        drive(stages, KafkaEvent.FIELD, "$.region", "east");

        assertEquals(List.of("SWITCH_VALUE", "FIELD($.region=east)"), events);
    }

    private void drive(
        KafkaTransform transform,
        KafkaEvent event,
        String path,
        String value)
    {
        content.wrap(path, value);
        transform.transform(control, content, event, recorder);
    }

    private static String describe(
        KafkaSource source,
        KafkaEvent event)
    {
        return event == KafkaEvent.FIELD
            ? String.format("FIELD(%s=%s)", source.getPath(), text(source.getValue()))
            : event.name();
    }

    private static String text(
        DirectBufferEx value)
    {
        return value.getStringWithoutLengthUtf8(0, value.capacity());
    }

    private static final class Content implements KafkaSource
    {
        private final UnsafeBufferEx view = new UnsafeBufferEx(new byte[0]);

        private String path;

        @Override
        public String getPath()
        {
            return path;
        }

        @Override
        public DirectBufferEx getValue()
        {
            return view;
        }

        private void wrap(
            String path,
            String value)
        {
            this.path = path;
            view.wrap(value != null ? value.getBytes(UTF_8) : new byte[0]);
        }
    }
}
