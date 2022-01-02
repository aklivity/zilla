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

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public final class AstVariantNode extends AstNamedNode
{
    private final AstType ofType;
    private final AstType kindType;
    private final List<AstVariantCaseNode> cases;
    private final AstByteOrder byteOrder;

    @Override
    public <R> R accept(
        Visitor<R> visitor)
    {
        return visitor.visitVariant(this);
    }

    @Override
    public Kind getKind()
    {
        return Kind.VARIANT;
    }

    public AstType of()
    {
        return ofType;
    }

    public AstType kindType()
    {
        return kindType;
    }

    public List<AstVariantCaseNode> cases()
    {
        return cases;
    }

    @Override
    public AstNamedNode withName(
        String name)
    {
        return new AstVariantNode(name, ofType, kindType, cases, byteOrder);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(name, ofType, kindType, cases, byteOrder);
    }

    public AstByteOrder byteOrder()
    {
        return byteOrder;
    }

    @Override
    public boolean equals(
        Object o)
    {
        if (o == this)
        {
            return true;
        }

        if (!(o instanceof AstVariantNode))
        {
            return false;
        }

        AstVariantNode that = (AstVariantNode) o;
        return Objects.equals(this.name, that.name) &&
            Objects.equals(this.cases, that.cases) &&
            Objects.equals(this.ofType, that.ofType) &&
            Objects.equals(this.kindType, that.kindType) &&
            Objects.equals(this.byteOrder, that.byteOrder);
    }

    private AstVariantNode(
        String name,
        AstType ofType,
        AstType kindType,
        List<AstVariantCaseNode> cases,
        AstByteOrder byteOrder)
    {
        super(name);
        this.ofType = ofType;
        this.kindType = kindType;
        this.cases = unmodifiableList(cases);
        this.byteOrder = byteOrder;
    }

    public static final class Builder extends AstNamedNode.Builder<AstVariantNode>
    {
        private AstType ofType;
        private List<AstVariantCaseNode> cases;
        private AstType kindType;
        private AstByteOrder byteOrder;

        public Builder()
        {
            this.cases = new LinkedList<>();
            this.byteOrder = NATIVE;
        }

        public Builder name(
            String name)
        {
            this.name = name;
            return this;
        }

        public Builder of(
            AstType ofType)
        {
            this.ofType = ofType;
            return this;
        }

        public Builder kindType(
            AstType kindType)
        {
            this.kindType = kindType;
            return this;
        }

        public Builder byteOrder(
            AstByteOrder byteOrder)
        {
            this.byteOrder = byteOrder;
            return this;
        }

        public Builder caseN(
            AstVariantCaseNode caseN)
        {
            this.cases.add(caseN);
            return this;
        }

        @Override
        public AstVariantNode build()
        {
            return new AstVariantNode(name, ofType, kindType, cases, byteOrder);
        }
    }
}
