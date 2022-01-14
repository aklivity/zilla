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
package io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.visit;

import static io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstNamedNode.Kind.VARIANT;
import static java.util.Collections.singleton;

import java.util.Collection;
import java.util.Set;

import com.squareup.javapoet.TypeName;

import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstEnumNode;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstNamedNode;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstNode;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstStructNode;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstType;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstUnionNode;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstVariantCaseNode;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstVariantNode;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.generate.TypeResolver;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.generate.TypeSpecGenerator;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.generate.VariantFlyweightGenerator;

public final class VariantVisitor extends AstNode.Visitor<Collection<TypeSpecGenerator<?>>>
{
    private final VariantFlyweightGenerator generator;
    private final TypeResolver resolver;
    private final Set<TypeSpecGenerator<?>> defaultResult;

    public VariantVisitor(
        VariantFlyweightGenerator generator,
        TypeResolver resolver)
    {
        this.generator = generator;
        this.resolver = resolver;
        this.defaultResult = singleton(generator);
    }

    @Override
    public Collection<TypeSpecGenerator<?>> visitStruct(
        AstStructNode structNode)
    {
        return defaultResult();
    }

    @Override
    public Collection<TypeSpecGenerator<?>> visitEnum(
        AstEnumNode enumNode)
    {
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
        return super.visitVariant(variantNode);
    }

    @Override
    public Collection<TypeSpecGenerator<?>> visitVariantCase(
        AstVariantCaseNode variantCaseNode)
    {
        Object value = variantCaseNode.value();
        AstType memberType = variantCaseNode.type();
        AstType mapKeyType = null;
        AstType mapValueType = null;
        AstNamedNode memberTypeNode = memberType == null ? null : resolver.resolve(memberType.name());
        if (memberTypeNode != null)
        {
            if (memberTypeNode.getKind() == VARIANT)
            {
                AstVariantNode varNode = (AstVariantNode) memberTypeNode;
                AstType ofType = varNode.of();
                if (AstType.MAP.equals(ofType))
                {
                    mapKeyType = variantCaseNode.typeParams().get(0);
                    mapValueType = variantCaseNode.typeParams().get(1);
                }
            }
        }

        int missingFieldValue = variantCaseNode.missingFieldValue();
        TypeName typeName = resolver.resolveType(memberType);
        TypeName unsignedTypeName = resolver.resolveUnsignedType(memberType);
        String memberTypeName = memberType != null ? memberType.name() : null;
        generator.addMember(value, memberTypeName, memberType, typeName, unsignedTypeName, missingFieldValue, mapKeyType,
            mapValueType);
        return defaultResult();
    }

    @Override
    protected Collection<TypeSpecGenerator<?>> defaultResult()
    {
        return defaultResult;
    }
}
