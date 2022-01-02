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

import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toList;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;

import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstAbstractMemberNode;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstByteOrder;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstEnumNode;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstNode;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstStructNode;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstType;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstUnionNode;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstVariantNode;
import io.aklivity.zilla.build.maven.plugins.cog.internal.generate.StructFlyweightGenerator;
import io.aklivity.zilla.build.maven.plugins.cog.internal.generate.TypeResolver;
import io.aklivity.zilla.build.maven.plugins.cog.internal.generate.TypeSpecGenerator;

public final class StructVisitor extends AstNode.Visitor<Collection<TypeSpecGenerator<?>>>
{
    private final StructFlyweightGenerator generator;
    private final TypeResolver resolver;
    private final Set<TypeSpecGenerator<?>> defaultResult;

    public StructVisitor(
        StructFlyweightGenerator generator,
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
        AstType supertype = structNode.supertype();
        if (supertype != null)
        {
            AstStructNode superNode = (AstStructNode) resolver.resolve(supertype.name());
            visitStruct(superNode);
        }

        super.visitStruct(structNode);
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
        return defaultResult();
    }

    @Override
    public Collection<TypeSpecGenerator<?>> visitMember(
        AstAbstractMemberNode memberNode)
    {
        String memberName = memberNode.name();
        AstType memberType = memberNode.type();
        int size = memberNode.size();
        String sizeName = memberNode.sizeName();
        TypeName sizeTypeName = memberNode.sizeType() == null ? null : memberNode.sizeType().isUnsignedInt() ?
            resolver.resolveUnsignedType(memberNode.sizeType()) : resolver.resolveType(memberNode.sizeType());

        boolean usedAsSize = memberNode.usedAsSize();
        Object defaultValue = memberNode.defaultValue();
        AstByteOrder byteOrder = memberNode.byteOrder();

        if (memberType == AstType.ARRAY32)
        {
            ClassName rawType = resolver.resolveClass(memberType);
            TypeName[] typeArguments = memberNode.types()
                    .stream()
                    .skip(1)
                    .map(resolver::resolveType)
                    .collect(toList())
                    .toArray(new TypeName[0]);
            ParameterizedTypeName memberTypeName = ParameterizedTypeName.get(rawType, typeArguments);
            List<AstType> memberTypes = memberNode.types();
            AstType memberUnsignedType = memberTypes.get(1);
            TypeName memberUnsignedTypeName = resolver.resolveUnsignedType(memberUnsignedType);
            generator.addMember(memberName, memberType, memberTypeName, memberUnsignedType, memberUnsignedTypeName, size,
                    sizeName, sizeTypeName, false, defaultValue, byteOrder);
        }
        else
        {
            TypeName memberTypeName = resolver.resolveType(memberType);
            if (memberTypeName == null)
            {
                throw new IllegalArgumentException(String.format(
                        " Unable to resolve type %s for field %s", memberType, memberName));
            }
            AstType memberUnsignedType = memberType.isUnsignedInt() ? memberType : null;
            TypeName memberUnsignedTypeName = resolver.resolveUnsignedType(memberUnsignedType);
            generator.addMember(memberName, memberType, memberTypeName, memberUnsignedType, memberUnsignedTypeName, size,
                    sizeName, sizeTypeName, usedAsSize, defaultValue, byteOrder);
        }

        return defaultResult();
    }

    @Override
    protected Collection<TypeSpecGenerator<?>> defaultResult()
    {
        return defaultResult;
    }
}
