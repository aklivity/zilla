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

import java.util.Objects;

public final class AstMapNode extends AstNamedNode
{
    private final AstType templateMapType;
    private final AstType keyType;
    private final AstType valueType;

    @Override
    public <R> R accept(
        Visitor<R> visitor)
    {
        return visitor.visitMap(this);
    }

    @Override
    public AstNamedNode withName(
        String name)
    {
        return new AstMapNode(name, templateMapType, keyType, valueType);
    }

    @Override
    public Kind getKind()
    {
        return Kind.MAP;
    }

    public AstType templateMapType()
    {
        return templateMapType;
    }

    public AstType keyType()
    {
        return keyType;
    }

    public AstType valueType()
    {
        return valueType;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(templateMapType, keyType, valueType);
    }

    @Override
    public boolean equals(
        Object o)
    {
        if (o == this)
        {
            return true;
        }

        if (!(o instanceof AstMapNode))
        {
            return false;
        }

        AstMapNode that = (AstMapNode) o;
        return Objects.equals(this.name, that.name) &&
            Objects.equals(this.templateMapType, that.templateMapType) &&
            Objects.equals(this.keyType, that.keyType) &&
            Objects.equals(this.valueType, that.valueType);
    }

    private AstMapNode(
        String name,
        AstType templateMapType,
        AstType keyType,
        AstType valueType)
    {
        super(name);
        this.templateMapType = templateMapType;
        this.keyType = keyType;
        this.valueType = valueType;
    }

    public static final class Builder extends AstNamedNode.Builder <AstMapNode>
    {
        private AstType templateMapType;
        private AstType keyType;
        private AstType valueType;

        @Override
        public Builder name(
            String name)
        {
            this.name = name;
            return this;
        }

        public Builder templateMapType(
            AstType templateMapType)
        {
            this.templateMapType = templateMapType;
            return this;
        }

        public Builder keyType(
            AstType keyType)
        {
            this.keyType = keyType;
            return this;
        }

        public Builder valueType(
            AstType valueType)
        {
            this.valueType = valueType;
            return this;
        }

        @Override
        public AstMapNode build()
        {
            return new AstMapNode(name, templateMapType, keyType, valueType);
        }
    }
}
