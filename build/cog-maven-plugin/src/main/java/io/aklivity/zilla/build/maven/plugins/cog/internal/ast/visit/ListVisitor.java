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
package io.aklivity.zilla.build.maven.plugins.cog.internal.ast.visit;

import static io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstNamedNode.Kind.MAP;
import static io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstNamedNode.Kind.VARIANT;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toList;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;

import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstAbstractMemberNode;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstByteOrder;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstEnumNode;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstListMemberNode;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstListNode;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstMapNode;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstNamedNode;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstNode;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstStructNode;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstType;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstUnionNode;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstVariantNode;
import io.aklivity.zilla.build.maven.plugins.cog.internal.generate.ListFlyweightGenerator;
import io.aklivity.zilla.build.maven.plugins.cog.internal.generate.TypeResolver;
import io.aklivity.zilla.build.maven.plugins.cog.internal.generate.TypeSpecGenerator;

public final class ListVisitor extends AstNode.Visitor<Collection<TypeSpecGenerator<?>>>
{
    private final ListFlyweightGenerator generator;
    private final TypeResolver resolver;
    private final Set<TypeSpecGenerator<?>> defaultResult;

    public ListVisitor(
        ListFlyweightGenerator generator,
        TypeResolver resolver)
    {
        this.generator = generator;
        this.resolver = resolver;
        this.defaultResult = singleton(generator);
    }

    @Override
    public Collection<TypeSpecGenerator<?>> visitList(
        AstListNode listNode)
    {
        super.visitList(listNode);
        return defaultResult();
    }

    @Override
    public Collection<TypeSpecGenerator<?>> visitStruct(
        AstStructNode structNode)
    {
        return defaultResult();
    }

    @Override
    public Collection<TypeSpecGenerator<?>> visitMember(
        AstAbstractMemberNode memberNode)
    {
        AstListMemberNode listMemberNode = (AstListMemberNode) memberNode;
        String memberName = listMemberNode.name();
        AstType memberType = listMemberNode.type();
        AstType arrayItemTypeName = null;
        AstType mapKeyType = null;
        AstType mapValueType = null;
        ClassName mapParamName = null;
        ClassName originalMapKeyName = null;
        ClassName originalMapValueName = null;

        boolean usedAsSize = listMemberNode.usedAsSize();
        Object defaultValue = listMemberNode.defaultValue();
        AstByteOrder byteOrder = listMemberNode.byteOrder();

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

            generator.addMember(memberName, memberType, memberTypeName, memberUnsignedTypeName, usedAsSize,
                defaultValue, byteOrder, listMemberNode.isRequired(), arrayItemTypeName, mapKeyType, mapValueType,
                mapParamName, originalMapKeyName, originalMapValueName);
        }
        else
        {
            AstNamedNode memberTypeNode = resolver.resolve(memberType.name());
            if (memberTypeNode != null)
            {
                if (memberTypeNode.getKind() == VARIANT)
                {
                    AstVariantNode varNode = (AstVariantNode) memberTypeNode;
                    AstType ofType = varNode.of();
                    if (AstType.ARRAY.equals(ofType))
                    {
                        AstType arrayType = listMemberNode.typeParams().get(0);
                        arrayItemTypeName = arrayType;
                    }
                    else if (AstType.MAP.equals(ofType))
                    {
                        mapKeyType = listMemberNode.typeParams().get(0);
                        mapValueType = listMemberNode.typeParams().get(1);
                    }
                }
                else if (memberTypeNode.getKind() == MAP)
                {
                    AstMapNode mapNode = (AstMapNode) memberTypeNode;
                    AstType keyType = mapNode.keyType();
                    AstType valueType = mapNode.valueType();
                    AstType mapParam = listMemberNode.typeParams().get(0);
                    mapParamName = resolver.resolveClass(mapParam);
                    originalMapKeyName = Objects.requireNonNullElse(resolver.resolveClass(keyType), mapParamName);
                    originalMapValueName = Objects.requireNonNullElse(resolver.resolveClass(valueType), mapParamName);
                }
            }

            TypeName memberTypeName = resolver.resolveType(memberType);
            if (memberTypeName == null)
            {
                throw new IllegalArgumentException(String.format(
                    " Unable to resolve type %s for field %s", memberType, memberName));
            }
            TypeName memberUnsignedTypeName = memberType.isUnsignedInt() ? resolver.resolveUnsignedType(memberType) : null;
            generator.addMember(memberName, memberType, memberTypeName, memberUnsignedTypeName, usedAsSize,
                defaultValue, byteOrder, listMemberNode.isRequired(), arrayItemTypeName, mapKeyType, mapValueType,
                mapParamName, originalMapKeyName, originalMapValueName);
        }
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
    protected Collection<TypeSpecGenerator<?>> defaultResult()
    {
        return defaultResult;
    }
}
