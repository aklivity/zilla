/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.build.maven.plugins.cog.internal.ast;

import static java.util.Objects.requireNonNull;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public final class AstVariantCaseNode extends AstNode
{
    private final Object value;
    private final AstType type;
    private final int missingFieldValue;
    private final List<AstType> typeParams;

    @Override
    public <R> R accept(
        Visitor<R> visitor)
    {
        return visitor.visitVariantCase(this);
    }

    public Object value()
    {
        return value;
    }

    public AstType type()
    {
        return type;
    }

    public int missingFieldValue()
    {
        return missingFieldValue;
    }

    public List<AstType> typeParams()
    {
        return typeParams;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(value, type, missingFieldValue, typeParams);
    }

    @Override
    public boolean equals(
        Object o)
    {
        if (o == this)
        {
            return true;
        }

        if (!(o instanceof AstVariantCaseNode))
        {
            return false;
        }

        AstVariantCaseNode that = (AstVariantCaseNode) o;
        return Objects.equals(this.value, that.value) &&
            Objects.equals(this.type, that.type) &&
            this.missingFieldValue == that.missingFieldValue &&
            Objects.equals(this.typeParams, that.typeParams);
    }

    private AstVariantCaseNode(
        Object value,
        AstType type,
        int missingFieldValue,
        List<AstType> typeParams)
    {
        this.value = value;
        this.type = type;
        this.missingFieldValue = missingFieldValue;
        this.typeParams = typeParams;
    }

    @Override
    public String toString()
    {
        return String.format("CASE [value=%s, type=%s, missingFieldValue=%s, typeParams=%s]",
            value, type, missingFieldValue, typeParams);
    }

    public static final class Builder extends AstNode.Builder<AstVariantCaseNode>
    {
        private Object value;
        private AstType type;
        private int missingFieldValue;
        private List<AstType> typeParams;

        public Builder()
        {
            super();
            this.typeParams = new LinkedList<>();
        }

        public Builder value(
            Object value)
        {
            this.value = value;
            return this;
        }

        public Builder type(
            AstType type)
        {
            this.type = type;
            return this;
        }

        public Builder typeParam(
            AstType typeParam)
        {
            typeParams.add(requireNonNull(typeParam));
            return this;
        }

        public Builder missingFieldValue(
            int missingFieldValue)
        {
            this.missingFieldValue = missingFieldValue;
            return this;
        }

        @Override
        public AstVariantCaseNode build()
        {
            return new AstVariantCaseNode(value, type, missingFieldValue, typeParams);
        }
    }
}
