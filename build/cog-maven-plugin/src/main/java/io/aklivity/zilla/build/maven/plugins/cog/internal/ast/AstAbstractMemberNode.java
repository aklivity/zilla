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
package io.aklivity.zilla.build.maven.plugins.cog.internal.ast;

import static io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstByteOrder.NATIVE;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public abstract class AstAbstractMemberNode extends AstNode
{
    public static final Object NULL_DEFAULT = new Object();

    protected final String name;
    protected final List<AstType> types;
    protected final int size;
    protected final String sizeName;
    protected final Object defaultValue;
    protected final AstByteOrder byteOrder;

    private AstType sizeType;
    private boolean usedAsSize;

    AstAbstractMemberNode(
        String name,
        List<AstType> types,
        int size,
        String sizeName,
        Object defaultValue,
        AstByteOrder byteOrder)
    {
        this.name = requireNonNull(name);
        this.types = unmodifiableList(requireNotEmpty(requireNonNull(types)));
        this.size = size;
        this.sizeName = sizeName;
        this.defaultValue = defaultValue;
        this.byteOrder = byteOrder;
    }

    @Override
    public <R> R accept(
        Visitor<R> visitor)
    {
        return visitor.visitMember(this);
    }

    public String name()
    {
        return name;
    }

    public AstType type()
    {
        return types.get(0);
    }

    public List<AstType> types()
    {
        return types;
    }

    public int size()
    {
        return size;
    }

    public String sizeName()
    {
        return sizeName;
    }

    public AstType sizeType()
    {
        return sizeType;
    }

    public void sizeType(
        AstType sizeType)
    {
        this.sizeType = sizeType;
    }

    public void usedAsSize(
        boolean value)
    {
        usedAsSize = value;
    }

    public boolean usedAsSize()
    {
        return usedAsSize;
    }

    public Object defaultValue()
    {
        return defaultValue;
    }

    public AstByteOrder byteOrder()
    {
        return byteOrder;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(name, types, sizeName, size, defaultValue, byteOrder);
    }

    @Override
    public boolean equals(
        Object o)
    {
        if (o == this)
        {
            return true;
        }

        if (!(o instanceof AstAbstractMemberNode))
        {
            return false;
        }

        AstAbstractMemberNode that = (AstAbstractMemberNode) o;
        return this.size == that.size &&
            Objects.equals(this.name, that.name) &&
            Objects.deepEquals(this.types, that.types) &&
            Objects.equals(this.sizeName, that.sizeName) &&
            Objects.equals(this.defaultValue, that.defaultValue) &&
            Objects.equals(this.byteOrder, that.byteOrder);
    }

    @Override
    public String toString()
    {
        String size = this.size == 0 ? this.sizeName : Integer.toString(this.size);
        return String.format("MEMBER [name=%s, size=%s, types=%s, defaultValue=%s, byteOrder=%s]",
            name, size, types, defaultValue, byteOrder);
    }

    private static <T extends Collection<?>> T requireNotEmpty(
        T c)
    {
        if (c.isEmpty())
        {
            throw new IllegalArgumentException();
        }

        return c;
    }

    public abstract static class Builder<T extends AstAbstractMemberNode> extends AstNode.Builder<T>
    {
        protected String name;
        protected List<AstType> types;
        protected int size;
        protected String sizeName;
        protected Object defaultValue;
        protected AstByteOrder byteOrder;

        public Builder()
        {
            this.types = new LinkedList<>();
            this.size = -1;
            this.byteOrder = NATIVE;
        }

        public Builder<T> name(
            String name)
        {
            this.name = name;
            return this;
        }

        public Builder<T> type(
            AstType type)
        {
            types.add(requireNonNull(type));
            return this;
        }

        public Builder<T> size(
            int size)
        {
            this.size = size;
            return this;
        }

        public Builder<T> sizeName(
            String sizeName)
        {
            this.sizeName = sizeName;
            return this;
        }

        public Builder<T> defaultValue(
            Object defaultValue)
        {
            this.defaultValue = defaultValue;
            return this;
        }

        public Builder<T> defaultToNull()
        {
            this.defaultValue = NULL_DEFAULT;
            return this;
        }

        public Builder<T> byteOrder(
            AstByteOrder byteOrder)
        {
            this.byteOrder = byteOrder;
            return this;
        }
    }
}
