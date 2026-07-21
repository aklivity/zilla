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
package io.aklivity.zilla.runtime.binding.mcp.kafka.internal.transform;

import java.math.BigDecimal;
import java.util.List;

import jakarta.json.stream.JsonLocation;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonVerbatim;

/**
 * Synthesizes a {@code consume} tool result envelope directly against a terminal {@link JsonSink}
 * (backed by a {@code JsonGeneratorEx} wrapped over a live encode slot), one Kafka record at a time,
 * without ever buffering the whole result in memory. There is no upstream JSON document to parse --
 * every byte comes from discrete Java calls ({@link #open}, {@link #record}, {@link #close}) driven
 * by {@code KafkaProxy} as Kafka fetch `DATA` frames arrive -- so this class drives {@code sink}
 * itself via a synthetic {@link JsonController}/{@link JsonSource} pair, the same injection idiom
 * {@code McpHttpResultWrap} uses for its own synthetic envelope bytes.
 * <p>
 * Envelope key order is deliberate: {@code structuredContent} (with {@code messages} before
 * {@code count}) precedes {@code content}, since the summary text embeds the final count and the
 * count itself is only known once every record has arrived -- both must be written last, after the
 * {@code messages} array has already streamed out record-by-record.
 */
public final class McpKafkaConsumeResult
{
    private static final int STEP_ROOT_START = 0;
    private static final int STEP_STRUCTURED_KEY = 1;
    private static final int STEP_STRUCTURED_START = 2;
    private static final int STEP_TOPIC_KEY = 3;
    private static final int STEP_TOPIC_VALUE = 4;
    private static final int STEP_MESSAGES_KEY = 5;
    private static final int STEP_MESSAGES_START = 6;
    private static final int STEP_RECORDS = 7;
    private static final int STEP_MESSAGES_END = 8;
    private static final int STEP_COUNT_KEY = 9;
    private static final int STEP_COUNT_VALUE = 10;
    private static final int STEP_STRUCTURED_END = 11;
    private static final int STEP_CONTENT_KEY = 12;
    private static final int STEP_CONTENT_START = 13;
    private static final int STEP_ITEM_START = 14;
    private static final int STEP_TYPE_KEY = 15;
    private static final int STEP_TYPE_VALUE = 16;
    private static final int STEP_TEXT_KEY = 17;
    private static final int STEP_TEXT_VALUE = 18;
    private static final int STEP_ITEM_END = 19;
    private static final int STEP_CONTENT_END = 20;
    private static final int STEP_ISERROR_KEY = 21;
    private static final int STEP_ISERROR_VALUE = 22;
    private static final int STEP_ROOT_END = 23;
    private static final int STEP_DONE = 24;

    private static final int REC_START = 0;
    private static final int REC_KEY_KEY = 1;
    private static final int REC_KEY_VALUE = 2;
    private static final int REC_HEADERS_KEY = 3;
    private static final int REC_HEADERS_START = 4;
    private static final int REC_HEADER_START = 5;
    private static final int REC_HEADER_NAME_KEY = 6;
    private static final int REC_HEADER_NAME_VALUE = 7;
    private static final int REC_HEADER_VALUE_KEY = 8;
    private static final int REC_HEADER_VALUE_VALUE = 9;
    private static final int REC_HEADER_END = 10;
    private static final int REC_HEADERS_END = 11;
    private static final int REC_VALUE_KEY = 12;
    private static final int REC_VALUE_VALUE = 13;
    private static final int REC_END = 14;
    private static final int REC_DONE = 15;

    private final Resumable text = new Resumable();
    private final Control inject = new Control();
    private final JsonSink sink;

    private int step = STEP_ROOT_START;
    private int recordStep = REC_DONE;
    private JsonEvent pendingEvent;

    private String topic;
    private int count;
    private String summaryText;
    private boolean isError;

    private String recKey;
    private List<String[]> recHeaders;
    private String recValue;
    private int headerIndex;

    public McpKafkaConsumeResult(
        JsonSink sink)
    {
        this.sink = sink;
    }

    public void reset()
    {
        step = STEP_ROOT_START;
        recordStep = REC_DONE;
        pendingEvent = null;
        headerIndex = 0;
    }

    public Status open(
        String topic)
    {
        this.topic = topic;
        return driveEnvelope();
    }

    public Status record(
        String key,
        List<String[]> headers,
        String value)
    {
        this.recKey = key;
        this.recHeaders = headers;
        this.recValue = value;
        this.headerIndex = 0;
        this.recordStep = REC_START;
        return driveRecord();
    }

    public Status resume()
    {
        return recordStep != REC_DONE ? driveRecord() : driveEnvelope();
    }

    public Status close(
        int count,
        String summaryText,
        boolean isError)
    {
        this.count = count;
        this.summaryText = summaryText;
        this.isError = isError;
        step = STEP_MESSAGES_END;
        return driveEnvelope();
    }

    private Status driveEnvelope()
    {
        Status status = Status.ADVANCED;
        if (pendingEvent != null)
        {
            status = sink.resume(inject, text, pendingEvent);
            if (status != Status.SUSPENDED)
            {
                pendingEvent = null;
                if (status != Status.REJECTED)
                {
                    step++;
                }
            }
        }

        while (status == Status.ADVANCED && step != STEP_RECORDS && step != STEP_DONE)
        {
            final JsonEvent event = armEnvelopeStep(step);
            status = sink.transform(inject, text, event);
            if (status == Status.SUSPENDED)
            {
                pendingEvent = event;
            }
            else if (status != Status.REJECTED)
            {
                step++;
            }
        }

        if (step == STEP_DONE)
        {
            status = Status.COMPLETED;
        }
        else if (step == STEP_RECORDS && status == Status.ADVANCED)
        {
            status = Status.STARVED;
        }

        return status;
    }

    private Status driveRecord()
    {
        Status status = Status.ADVANCED;
        if (pendingEvent != null)
        {
            status = sink.resume(inject, text, pendingEvent);
            if (status != Status.SUSPENDED)
            {
                pendingEvent = null;
                if (status != Status.REJECTED)
                {
                    advanceRecordStep();
                }
            }
        }

        while (status == Status.ADVANCED && recordStep != REC_DONE)
        {
            final JsonEvent event = armRecordStep(recordStep);
            status = sink.transform(inject, text, event);
            if (status == Status.SUSPENDED)
            {
                pendingEvent = event;
            }
            else if (status != Status.REJECTED)
            {
                advanceRecordStep();
            }
        }

        return status;
    }

    private void advanceRecordStep()
    {
        switch (recordStep)
        {
        case REC_HEADERS_START:
        case REC_HEADER_END:
            if (recordStep == REC_HEADER_END)
            {
                headerIndex++;
            }
            recordStep = headerIndex < recHeaders.size() ? REC_HEADER_START : REC_HEADERS_END;
            break;
        default:
            recordStep++;
            break;
        }
    }

    private JsonEvent armEnvelopeStep(
        int step)
    {
        JsonEvent event;
        switch (step)
        {
        case STEP_ROOT_START:
            text.with("");
            event = JsonEvent.START_OBJECT;
            break;
        case STEP_STRUCTURED_KEY:
            text.with("structuredContent");
            event = JsonEvent.KEY_NAME;
            break;
        case STEP_STRUCTURED_START:
            text.with("");
            event = JsonEvent.START_OBJECT;
            break;
        case STEP_TOPIC_KEY:
            text.with("topic");
            event = JsonEvent.KEY_NAME;
            break;
        case STEP_TOPIC_VALUE:
            text.with(topic);
            event = JsonEvent.VALUE_STRING;
            break;
        case STEP_MESSAGES_KEY:
            text.with("messages");
            event = JsonEvent.KEY_NAME;
            break;
        case STEP_MESSAGES_START:
            text.with("");
            event = JsonEvent.START_ARRAY;
            break;
        case STEP_MESSAGES_END:
            text.with("");
            event = JsonEvent.END_ARRAY;
            break;
        case STEP_COUNT_KEY:
            text.with("count");
            event = JsonEvent.KEY_NAME;
            break;
        case STEP_COUNT_VALUE:
            text.with(Integer.toString(count));
            event = JsonEvent.VALUE_NUMBER;
            break;
        case STEP_STRUCTURED_END:
            text.with("");
            event = JsonEvent.END_OBJECT;
            break;
        case STEP_CONTENT_KEY:
            text.with("content");
            event = JsonEvent.KEY_NAME;
            break;
        case STEP_CONTENT_START:
            text.with("");
            event = JsonEvent.START_ARRAY;
            break;
        case STEP_ITEM_START:
            text.with("");
            event = JsonEvent.START_OBJECT;
            break;
        case STEP_TYPE_KEY:
            text.with("type");
            event = JsonEvent.KEY_NAME;
            break;
        case STEP_TYPE_VALUE:
            text.with("text");
            event = JsonEvent.VALUE_STRING;
            break;
        case STEP_TEXT_KEY:
            text.with("text");
            event = JsonEvent.KEY_NAME;
            break;
        case STEP_TEXT_VALUE:
            text.with(summaryText);
            event = JsonEvent.VALUE_STRING;
            break;
        case STEP_ITEM_END:
            text.with("");
            event = JsonEvent.END_OBJECT;
            break;
        case STEP_CONTENT_END:
            text.with("");
            event = JsonEvent.END_ARRAY;
            break;
        case STEP_ISERROR_KEY:
            text.with("isError");
            event = JsonEvent.KEY_NAME;
            break;
        case STEP_ISERROR_VALUE:
            text.with("");
            event = isError ? JsonEvent.VALUE_TRUE : JsonEvent.VALUE_FALSE;
            break;
        case STEP_ROOT_END:
            text.with("");
            event = JsonEvent.END_OBJECT;
            break;
        default:
            throw new IllegalStateException("unexpected step: " + step);
        }
        return event;
    }

    private JsonEvent armRecordStep(
        int step)
    {
        JsonEvent event;
        switch (step)
        {
        case REC_START:
            text.with("");
            event = JsonEvent.START_OBJECT;
            break;
        case REC_KEY_KEY:
            text.with("key");
            event = JsonEvent.KEY_NAME;
            break;
        case REC_KEY_VALUE:
            if (recKey != null)
            {
                text.with(recKey);
                event = JsonEvent.VALUE_STRING;
            }
            else
            {
                text.with("");
                event = JsonEvent.VALUE_NULL;
            }
            break;
        case REC_HEADERS_KEY:
            text.with("headers");
            event = JsonEvent.KEY_NAME;
            break;
        case REC_HEADERS_START:
            text.with("");
            event = JsonEvent.START_ARRAY;
            break;
        case REC_HEADER_START:
            text.with("");
            event = JsonEvent.START_OBJECT;
            break;
        case REC_HEADER_NAME_KEY:
            text.with("name");
            event = JsonEvent.KEY_NAME;
            break;
        case REC_HEADER_NAME_VALUE:
        {
            final String name = recHeaders.get(headerIndex)[0];
            if (name != null)
            {
                text.with(name);
                event = JsonEvent.VALUE_STRING;
            }
            else
            {
                text.with("");
                event = JsonEvent.VALUE_NULL;
            }
            break;
        }
        case REC_HEADER_VALUE_KEY:
            text.with("value");
            event = JsonEvent.KEY_NAME;
            break;
        case REC_HEADER_VALUE_VALUE:
        {
            final String value = recHeaders.get(headerIndex)[1];
            if (value != null)
            {
                text.with(value);
                event = JsonEvent.VALUE_STRING;
            }
            else
            {
                text.with("");
                event = JsonEvent.VALUE_NULL;
            }
            break;
        }
        case REC_HEADER_END:
            text.with("");
            event = JsonEvent.END_OBJECT;
            break;
        case REC_HEADERS_END:
            text.with("");
            event = JsonEvent.END_ARRAY;
            break;
        case REC_VALUE_KEY:
            text.with("value");
            event = JsonEvent.KEY_NAME;
            break;
        case REC_VALUE_VALUE:
            text.with(recValue);
            event = JsonEvent.VALUE_STRING;
            break;
        case REC_END:
            text.with("");
            event = JsonEvent.END_OBJECT;
            break;
        default:
            throw new IllegalStateException("unexpected record step: " + step);
        }
        return event;
    }

    // A resumable synthetic JsonSource: getStringView() shrinks by however much consumed() has
    // reported, so a step suspended mid-write resumes from exactly where it left off.
    private static final class Resumable implements JsonSource
    {
        private CharSequence value;
        private int offset;

        private Resumable with(
            CharSequence value)
        {
            this.value = value;
            this.offset = 0;
            return this;
        }

        private void consumed(
            int count)
        {
            offset += count;
        }

        @Override
        public String getString()
        {
            return value == null ? null : getStringView().toString();
        }

        @Override
        public CharSequence getStringView()
        {
            return value.subSequence(offset, value.length());
        }

        @Override
        public boolean deferredBytes()
        {
            return false;
        }

        @Override
        public BigDecimal getBigDecimal()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isIntegralNumber()
        {
            return true;
        }

        @Override
        public int getInt()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getLong()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonLocation getLocation()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public DirectBufferEx getSegment()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonVerbatim getVerbatim(
            int limit)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void skipValue()
        {
            throw new UnsupportedOperationException();
        }
    }

    // Every event injected by this class is synthetic, so segmentable()/verbatim() requests from the
    // sink are simply declined and consumed() routes back to the Resumable's own offset.
    private final class Control implements JsonController
    {
        @Override
        public void segmentable()
        {
        }

        @Override
        public void verbatim()
        {
        }

        @Override
        public void consumed(
            int sourceBytes)
        {
            text.consumed(sourceBytes);
        }
    }
}
