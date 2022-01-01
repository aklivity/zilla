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

public abstract class AstNamedNode extends AstNode
{
    public enum Kind
    {
        SCOPE, STRUCT, UNION, VARIANT, LIST, ENUM, TYPEDEF, MAP, DEFAULT
    }
    protected final String name;

    AstNamedNode(
        String name)
    {
        this.name = requireNonNull(name);
    }

    public abstract AstNamedNode withName(String name);

    public String name()
    {
        return name;
    }

    public abstract Kind getKind();

    public abstract static class Builder<T extends AstNamedNode> extends AstNode.Builder<T>
    {
        protected String name;

        public abstract Builder<T> name(String name);
    }
}
