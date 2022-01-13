/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.internal.registry.json;

import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import jakarta.json.stream.JsonLocation;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;

import org.leadpony.justify.api.Evaluator;
import org.leadpony.justify.api.EvaluatorContext;
import org.leadpony.justify.api.InstanceType;
import org.leadpony.justify.api.JsonSchema;
import org.leadpony.justify.api.Problem;
import org.leadpony.justify.api.ProblemDispatcher;

public final class UniquePropertyKeysSchema extends SchemaDecorator
{
    public UniquePropertyKeysSchema(
        JsonSchema delegate)
    {
        super(delegate);
    }

    @Override
    public Evaluator createEvaluator(
        EvaluatorContext context,
        InstanceType type)
    {
        Evaluator evaluator = super.createEvaluator(context, type);

        if (type == InstanceType.ARRAY ||
            type == InstanceType.OBJECT)
        {
            evaluator = new UniquePropertyKeysEvaluator(evaluator, context, type);
        }

        return evaluator;
    }

    private final class UniquePropertyKeysEvaluator implements Evaluator
    {
        private final Evaluator delegate;
        private final EvaluatorContext context;

        private final Deque<Set<String>> stack = new LinkedList<>();

        private UniquePropertyKeysEvaluator(
            Evaluator delegate,
            EvaluatorContext context,
            InstanceType type)
        {
            this.delegate = delegate;
            this.context = context;
        }

        @Override
        public Result evaluate(
            Event event,
            int depth,
            ProblemDispatcher dispatcher)
        {
            switch (event)
            {
            case START_OBJECT:
                stack.push(new HashSet<>());
                break;
            case KEY_NAME:
                JsonParser parser = context.getParser();
                String key = parser.getString();
                if (!stack.peek().add(key))
                {
                    String pointer = context.getPointer();
                    JsonLocation location = parser.getLocation();
                    long line = location.getLineNumber();
                    long column = location.getColumnNumber();
                    dispatcher.dispatchProblem(new Problem()
                    {

                        @Override
                        public String getMessage(
                            Locale locale)
                        {
                            return String.format("Duplicate key '%s' is not allowed", key);
                        }

                        @Override
                        public String getContextualMessage(
                            Locale locale)
                        {
                            return String.format("[%d,%d][%s] Duplicate key '%s' is not allowed", line, column, pointer, key);
                        }

                        @Override
                        public void print(
                            Consumer<String> lineConsumer,
                            Locale locale)
                        {
                        }

                        @Override
                        public JsonLocation getLocation()
                        {
                            return location;
                        }

                        @Override
                        public String getPointer()
                        {
                            return pointer;
                        }

                        @Override
                        public JsonSchema getSchema()
                        {
                            return UniquePropertyKeysSchema.this;
                        }

                        @Override
                        public String getKeyword()
                        {
                            return null;
                        }

                        @Override
                        public Map<String, ?> parametersAsMap()
                        {
                            return null;
                        }

                        @Override
                        public boolean isResolvable()
                        {
                            return false;
                        }
                    });
                }
                break;
            case END_OBJECT:
                stack.pop();
                break;
            default:
                break;
            }

            return delegate.evaluate(event, depth, dispatcher);
        }
    }
}
