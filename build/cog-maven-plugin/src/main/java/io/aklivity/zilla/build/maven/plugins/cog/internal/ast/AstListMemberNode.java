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

import static java.util.Objects.requireNonNull;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public final class AstListMemberNode extends AstAbstractMemberNode
{
    private final List<AstType> typeParams;
    private final boolean required;

    private AstListMemberNode(
        String name,
        List<AstType> types,
        int size,
        String sizeName,
        Object defaultValue,
        AstByteOrder byteOrder,
        List<AstType> typeParams,
        boolean required)
    {
        super(name, types, size, sizeName, defaultValue, byteOrder);
        this.typeParams = typeParams;
        this.required = required;
    }

    public List<AstType> typeParams()
    {
        return typeParams;
    }

    public boolean isRequired()
    {
        return required;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(name, types, defaultValue, byteOrder, typeParams, required);
    }

    @Override
    public boolean equals(
        Object o)
    {
        if (o == this)
        {
            return true;
        }

        if (!(o instanceof AstListMemberNode))
        {
            return false;
        }

        AstListMemberNode that = (AstListMemberNode) o;
        return this.required == that.required &&
            Objects.equals(this.name, that.name) &&
            Objects.deepEquals(this.types, that.types) &&
            Objects.equals(this.defaultValue, that.defaultValue) &&
            Objects.equals(this.byteOrder, that.byteOrder) &&
            Objects.equals(this.typeParams, that.typeParams);
    }

    @Override
    public String toString()
    {
        return String.format("MEMBER [name=%s, types=%s, defaultValue=%s, byteOrder=%s, typeParams=%s, required=%s]",
            name, types, defaultValue, byteOrder, typeParams, required);
    }

    public static final class Builder extends AstAbstractMemberNode.Builder<AstListMemberNode>
    {
        private List<AstType> typeParams;
        private boolean required;

        public Builder()
        {
            super();
            this.typeParams = new LinkedList<>();
        }

        public Builder typeParam(
            AstType typeParam)
        {
            typeParams.add(requireNonNull(typeParam));
            return this;
        }

        public Builder isRequired(
            boolean required)
        {
            this.required = required;
            return this;
        }

        @Override
        public AstListMemberNode build()
        {
            return new AstListMemberNode(name, types, size, sizeName, defaultValue, byteOrder, typeParams, required);
        }
    }
}
