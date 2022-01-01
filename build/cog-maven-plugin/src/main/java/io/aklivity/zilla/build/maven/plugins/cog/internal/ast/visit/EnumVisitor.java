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
package io.aklivity.zilla.build.maven.plugins.cog.internal.ast.visit;

import static java.util.Arrays.asList;

import java.util.Collection;
import java.util.List;

import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstEnumNode;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstNode;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstUnionNode;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstValueNode;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstVariantNode;
import io.aklivity.zilla.build.maven.plugins.cog.internal.generate.EnumFlyweightGenerator;
import io.aklivity.zilla.build.maven.plugins.cog.internal.generate.EnumTypeGenerator;
import io.aklivity.zilla.build.maven.plugins.cog.internal.generate.TypeSpecGenerator;

public final class EnumVisitor extends AstNode.Visitor<Collection<TypeSpecGenerator<?>>>
{
    private final EnumTypeGenerator typeGenerator;
    private final List<TypeSpecGenerator<?>> defaultResult;

    public EnumVisitor(
        EnumTypeGenerator typeGenerator,
        EnumFlyweightGenerator flyweightGenerator)
    {
        this.typeGenerator = typeGenerator;
        this.defaultResult = asList(flyweightGenerator, typeGenerator);
    }

    @Override
    public Collection<TypeSpecGenerator<?>> visitEnum(
        AstEnumNode enumNode)
    {
        super.visitEnum(enumNode);
        return defaultResult();
    }

    @Override
    public Collection<TypeSpecGenerator<?>> visitUnion(
        AstUnionNode unionNode)
    {
        return defaultResult();
    }

    @Override
    public Collection<TypeSpecGenerator<?>> visitVariant(
        AstVariantNode variantNode)
    {
        return defaultResult();
    }

    @Override
    public Collection<TypeSpecGenerator<?>> visitValue(
        AstValueNode valueNode)
    {
        typeGenerator.addValue(valueNode.name(), valueNode.value());

        return defaultResult();
    }

    @Override
    protected Collection<TypeSpecGenerator<?>> defaultResult()
    {
        return defaultResult;
    }
}
