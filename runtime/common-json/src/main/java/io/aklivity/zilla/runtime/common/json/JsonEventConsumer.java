/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.common.json;

import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;

/**
 * A push event-driven stage in a {@code common-json} processing chain. Each call to {@link
 * #feed(Event, JsonParser)} delivers exactly one {@link JsonParser.Event} (with the parser
 * positioned just past that event, so scalar text is readable via {@link JsonParser#getString()})
 * and returns whether the consumer has reached a terminal verdict for the current top-level
 * value.
 * <p>
 * Stages compose linearly — a validator that forwards every event unchanged, then a projector
 * that drops events outside retained paths, then a generator-sink that materializes the kept
 * events into a buffer. The driver pulls events from a {@link JsonParser} and feeds the root
 * stage; each stage decides what (if anything) to forward to its downstream stage.
 */
public interface JsonEventConsumer
{
    enum Status
    {
        /** the current top-level value is still in progress */
        PENDING,
        /** the current top-level value finished and was accepted */
        COMPLETE,
        /** the current top-level value was rejected; further feeds are ignored */
        REJECTED
    }

    Status feed(
        Event event,
        JsonParser parser);

    /**
     * Resets internal state so the consumer can process a fresh top-level value. The default
     * propagates the reset to no downstream — stages with a sink should override.
     */
    default void reset()
    {
    }

    /**
     * Feeds every event currently available from {@code parser} until this consumer reaches a
     * terminal {@link Status} for the in-progress top-level value, or the parser yields no further
     * event right now (returning {@link Status#PENDING}). Does <em>not</em> reset, so it resumes a
     * value left in progress by an earlier call — the pump model for slot-fragmented input, where
     * each network frame appends bytes to the underlying parser and re-invokes the pump. Call
     * {@link #reset()} once before the first value, then {@code pump} per frame; for the one-shot,
     * complete-buffer case call {@code reset()} then a single {@code pump}.
     */
    default Status pump(
        JsonParser parser)
    {
        Status status = Status.PENDING;
        while (status == Status.PENDING && parser.hasNext())
        {
            status = feed(parser.next(), parser);
        }
        return status;
    }

    /**
     * Wraps a {@link JsonGeneratorEx} as a {@link JsonEventConsumer} sink that materializes
     * each fed event into the corresponding {@code writeXxx} call. The supplied generator must
     * already be wrapped over its target buffer.
     */
    static JsonEventConsumer of(
        JsonGeneratorEx generator)
    {
        return new GeneratorSink(generator);
    }

    final class GeneratorSink implements JsonEventConsumer
    {
        private final JsonGeneratorEx generator;
        private int depth;

        GeneratorSink(
            JsonGeneratorEx generator)
        {
            this.generator = generator;
        }

        @Override
        public Status feed(
            Event event,
            JsonParser parser)
        {
            Status status = Status.PENDING;
            switch (event)
            {
            case KEY_NAME:
                generator.writeKey(parser.getString());
                break;
            case START_OBJECT:
                generator.writeStartObject();
                depth++;
                break;
            case START_ARRAY:
                generator.writeStartArray();
                depth++;
                break;
            case END_OBJECT:
            case END_ARRAY:
                generator.writeEnd();
                depth--;
                if (depth == 0)
                {
                    status = Status.COMPLETE;
                }
                break;
            case VALUE_STRING:
                generator.write(parser.getString());
                status = scalarStatus();
                break;
            case VALUE_NUMBER:
                generator.writeNumber(parser.getString());
                status = scalarStatus();
                break;
            case VALUE_TRUE:
                generator.write(true);
                status = scalarStatus();
                break;
            case VALUE_FALSE:
                generator.write(false);
                status = scalarStatus();
                break;
            case VALUE_NULL:
                generator.writeNull();
                status = scalarStatus();
                break;
            default:
                break;
            }
            return status;
        }

        @Override
        public void reset()
        {
            depth = 0;
        }

        private Status scalarStatus()
        {
            return depth == 0 ? Status.COMPLETE : Status.PENDING;
        }
    }
}
