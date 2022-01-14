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
package io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast;

import static java.util.Objects.requireNonNull;

import java.util.Objects;

public final class AstValueNode extends AstNode
{
    private final String name;
    private final int ordinal;
    private final Object value;

    private AstValueNode(
        String name,
        int ordinal,
        Object value)
    {
        this.name = requireNonNull(name);
        this.ordinal = ordinal;
        this.value = value;
    }

    @Override
    public <R> R accept(
        Visitor<R> visitor)
    {
        return visitor.visitValue(this);
    }

    public String name()
    {
        return name;
    }

    public int size()
    {
        return ordinal;
    }

    public Object value()
    {
        return value;
    }

    @Override
    public int hashCode()
    {
        return (name.hashCode() << 3) ^ ordinal;
    }

    @Override
    public boolean equals(Object o)
    {
        if (o == this)
        {
            return true;
        }

        if (!(o instanceof AstValueNode))
        {
            return false;
        }

        AstValueNode that = (AstValueNode) o;
        return this.ordinal == that.ordinal &&
                Objects.equals(this.name, that.name);
    }

    @Override
    public String toString()
    {
        return String.format("VALUE [name=%s, ordinal=%d, value=%s]", name, ordinal, value);
    }

    public static final class Builder extends AstNode.Builder<AstValueNode>
    {
        private String name;
        private int ordinal;
        private Object value;

        public Builder name(String name)
        {
            this.name = name;
            return this;
        }

        public Builder ordinal(int ordinal)
        {
            this.ordinal = ordinal;
            return this;
        }

        public Builder value(Object value)
        {
            this.value = value;
            return this;
        }

        @Override
        public AstValueNode build()
        {
            return new AstValueNode(name, ordinal, value);
        }
    }
}
