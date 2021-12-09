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

import static java.util.Collections.unmodifiableList;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public final class AstEnumNode extends AstNamedNode
{
    private final List<AstValueNode> values;
    private final AstType valueType;

    @Override
    public <R> R accept(
        Visitor<R> visitor)
    {
        return visitor.visitEnum(this);
    }

    public List<AstValueNode> values()
    {
        return values;
    }

    public AstType valueType()
    {
        return valueType;
    }

    @Override
    public AstNamedNode withName(
        String name)
    {
        return new AstEnumNode(name, values, valueType);
    }

    @Override
    public Kind getKind()
    {
        return Kind.ENUM;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(name, values, valueType);
    }

    @Override
    public boolean equals(Object o)
    {
        if (o == this)
        {
            return true;
        }

        if (!(o instanceof AstEnumNode))
        {
            return false;
        }

        AstEnumNode that = (AstEnumNode) o;
        return Objects.equals(this.name, that.name) &&
            Objects.equals(this.values, that.values) &&
            Objects.equals(this.valueType, that.valueType);
    }

    @Override
    public String toString()
    {
        return String.format("ENUM [name=%s, values=%s, valueType=%s]", name, values, valueType);
    }

    private AstEnumNode(
        String name,
        List<AstValueNode> values,
        AstType valueType)
    {
        super(name);
        this.values = unmodifiableList(values);
        this.valueType = valueType;
    }

    public static final class Builder extends AstNamedNode.Builder<AstEnumNode>
    {
        private List<AstValueNode> values;
        private AstType valueType;

        public Builder()
        {
            this.values = new LinkedList<>();
        }

        public Builder name(
            String name)
        {
            this.name = name;
            return this;
        }

        public Builder value(
            AstValueNode value)
        {
            this.values.add(value);
            return this;
        }

        public Builder valueType(
            AstType valueType)
        {
            this.valueType = valueType;
            return this;
        }

        public AstType valueType()
        {
            return valueType;
        }

        public int size()
        {
            return values.size();
        }

        @Override
        public AstEnumNode build()
        {
            return new AstEnumNode(name, values, valueType);
        }
    }
}
