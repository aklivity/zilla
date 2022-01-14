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

public final class AstSpecificationNode extends AstNode
{
    private final AstScopeNode scope;

    @Override
    public <R> R accept(
        Visitor<R> visitor)
    {
        return visitor.visitSpecification(this);
    }

    public AstScopeNode scope()
    {
        return scope;
    }

    @Override
    public int hashCode()
    {
        return scope.hashCode();
    }

    @Override
    public boolean equals(Object o)
    {
        if (o == this)
        {
            return true;
        }

        if (!(o instanceof AstSpecificationNode))
        {
            return false;
        }

        AstSpecificationNode that = (AstSpecificationNode) o;
        return Objects.equals(this.scope, that.scope);
    }

    private AstSpecificationNode(
        AstScopeNode scope)
    {
        this.scope = requireNonNull(scope);
    }

    public static final class Builder extends AstNode.Builder<AstSpecificationNode>
    {
        private AstScopeNode scope;

        public Builder scope(AstScopeNode scope)
        {
            this.scope = scope;
            return this;
        }

        @Override
        public AstSpecificationNode build()
        {
            return new AstSpecificationNode(scope);
        }
    }
}
