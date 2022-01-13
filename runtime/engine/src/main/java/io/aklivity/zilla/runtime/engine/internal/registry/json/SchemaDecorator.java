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

import java.net.URI;
import java.util.stream.Stream;

import jakarta.json.JsonValue;
import jakarta.json.JsonValue.ValueType;

import org.leadpony.justify.api.Evaluator;
import org.leadpony.justify.api.EvaluatorContext;
import org.leadpony.justify.api.InstanceType;
import org.leadpony.justify.api.JsonSchema;
import org.leadpony.justify.api.ObjectJsonSchema;

abstract class SchemaDecorator implements JsonSchema
{
    private final JsonSchema delegate;

    protected SchemaDecorator(
        JsonSchema delegate)
    {
        this.delegate = delegate;
    }

    @Override
    public boolean isBoolean()
    {
        return delegate.isBoolean();
    }

    @Override
    public boolean hasId()
    {
        return delegate.hasId();
    }

    @Override
    public boolean hasAbsoluteId()
    {
        return delegate.hasAbsoluteId();
    }

    @Override
    public URI id()
    {
        return delegate.id();
    }

    @Override
    public URI schema()
    {
        return delegate.schema();
    }

    @Override
    public String comment()
    {
        return delegate.comment();
    }

    @Override
    public String title()
    {
        return delegate.title();
    }

    @Override
    public String description()
    {
        return delegate.description();
    }

    @Override
    public JsonValue defaultValue()
    {
        return delegate.defaultValue();
    }

    @Override
    public ObjectJsonSchema asObjectJsonSchema()
    {
        return delegate.asObjectJsonSchema();
    }

    @Override
    public boolean containsKeyword(
        String keyword)
    {
        return delegate.containsKeyword(keyword);
    }

    @Override
    public JsonValue getKeywordValue(
        String keyword)
    {
        return delegate.getKeywordValue(keyword);
    }

    @Override
    public JsonValue getKeywordValue(
        String keyword,
        JsonValue defaultValue)
    {
        return delegate.getKeywordValue(keyword, defaultValue);
    }

    @Override
    public Stream<JsonSchema> getSubschemas()
    {
        return delegate.getSubschemas();
    }

    @Override
    public Stream<JsonSchema> getInPlaceSubschemas()
    {
        return delegate.getInPlaceSubschemas();
    }

    @Override
    public JsonSchema getSubschemaAt(String jsonPointer)
    {
        return delegate.getSubschemaAt(jsonPointer);
    }

    @Override
    public Evaluator createEvaluator(
        EvaluatorContext context,
        InstanceType type)
    {
        return delegate.createEvaluator(context, type);
    }

    @Override
    public Evaluator createNegatedEvaluator(
        EvaluatorContext context,
        InstanceType type)
    {
        return delegate.createNegatedEvaluator(context, type);
    }

    @Override
    public ValueType getJsonValueType()
    {
        return delegate.getJsonValueType();
    }

    @Override
    public JsonValue toJson()
    {
        return delegate.toJson();
    }
}
