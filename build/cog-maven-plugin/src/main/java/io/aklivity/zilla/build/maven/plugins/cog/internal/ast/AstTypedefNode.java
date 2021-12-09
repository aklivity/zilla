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

import java.util.Objects;

public final class AstTypedefNode extends AstNamedNode
{
    private final AstType originalType;

    @Override
    public <R> R accept(
        Visitor<R> visitor)
    {
        return visitor.visitTypedef(this);
    }

    public AstType originalType()
    {
        return originalType;
    }

    @Override
    public AstNamedNode withName(
        String name)
    {
        return new AstTypedefNode(name, originalType);
    }

    @Override
    public Kind getKind()
    {
        return Kind.TYPEDEF;
    }

    @Override
    public int hashCode()
    {
        return name.hashCode() << 7 ^ originalType.hashCode() << 3;
    }

    @Override
    public boolean equals(
        Object o)
    {
        if (o == this)
        {
            return true;
        }

        if (!(o instanceof AstTypedefNode))
        {
            return false;
        }

        AstTypedefNode that = (AstTypedefNode) o;
        return Objects.equals(this.name, that.name) &&
            Objects.equals(this.originalType, that.originalType);
    }

    private AstTypedefNode(
        String name,
        AstType originalType)
    {
        super(name);
        this.originalType = originalType;
    }

    public static final class Builder extends AstNamedNode.Builder<AstTypedefNode>
    {
        private AstType originalType;

        public Builder originalType(
            AstType originalType)
        {
            this.originalType = originalType;
            return this;
        }

        @Override
        public Builder name(
            String name)
        {
            this.name = name;
            return this;
        }

        @Override
        public AstTypedefNode build()
        {
            return new AstTypedefNode(name, originalType);
        }
    }
}
