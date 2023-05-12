/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.build.maven.plugins.flyweight.internal.generate;

import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstAbstractMemberNode.NULL_DEFAULT;
import static io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstByteOrder.NETWORK;
import static io.aklivity.zilla.build.maven.plugins.flyweight.internal.generate.TypeNames.BIT_UTIL_TYPE;
import static io.aklivity.zilla.build.maven.plugins.flyweight.internal.generate.TypeNames.BUFFER_UTIL_TYPE;
import static io.aklivity.zilla.build.maven.plugins.flyweight.internal.generate.TypeNames.BYTE_ARRAY;
import static io.aklivity.zilla.build.maven.plugins.flyweight.internal.generate.TypeNames.DIRECT_BUFFER_TYPE;
import static io.aklivity.zilla.build.maven.plugins.flyweight.internal.generate.TypeNames.MUTABLE_DIRECT_BUFFER_TYPE;
import static io.aklivity.zilla.build.maven.plugins.flyweight.internal.generate.TypeNames.UNSAFE_BUFFER_TYPE;
import static java.lang.String.format;
import static java.util.Collections.unmodifiableMap;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.FieldSpec.Builder;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;

import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstByteOrder;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstNamedNode;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstNamedNode.Kind;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstType;

public final class UnionFlyweightGenerator extends ClassSpecGenerator
{
    private static final Set<String> RESERVED_METHOD_NAMES = new HashSet<>(Arrays.asList(new String[]
    {
        "offset", "buffer", "limit", "sizeof", "maxLimit", "wrap", "checkLimit", "build", "rewrap"
    }));

    private final String baseName;
    private final TypeSpec.Builder builder;
    private final MemberFieldGenerator memberField;
    private final KindConstantGenerator memberKindConstant;
    private final MemberSizeConstantGenerator memberSizeConstant;
    private final MemberOffsetConstantGenerator memberOffsetConstant;
    private final KindAccessorGenerator kindAccessor;
    private final MemberAccessorGenerator memberAccessor;
    private final TryWrapMethodGenerator tryWrapMethod;
    private final WrapMethodGenerator wrapMethod;
    private final LimitMethodGenerator limitMethod;
    private final ToStringMethodGenerator toStringMethod;
    private final BuilderClassGenerator builderClass;

    public UnionFlyweightGenerator(
        ClassName unionName,
        ClassName flyweightName,
        String baseName,
        AstType superType,
        TypeName kindTypeName,
        TypeResolver resolver)
    {
        super(unionName);

        this.baseName = baseName;
        this.builder = classBuilder(unionName).superclass(flyweightName).addModifiers(PUBLIC, FINAL);
        this.memberKindConstant = new KindConstantGenerator(unionName, kindTypeName, builder);
        this.memberSizeConstant = new MemberSizeConstantGenerator(unionName, superType, kindTypeName, builder);
        this.memberOffsetConstant = new MemberOffsetConstantGenerator(unionName, superType, kindTypeName, builder);
        this.memberField = new MemberFieldGenerator(unionName, kindTypeName, builder);
        this.kindAccessor = new KindAccessorGenerator(unionName, kindTypeName, builder);
        this.memberAccessor = new MemberAccessorGenerator(unionName, flyweightName.nestedClass("Visitor"), builder);
        this.tryWrapMethod = new TryWrapMethodGenerator(kindTypeName);
        this.wrapMethod = new WrapMethodGenerator(kindTypeName);
        this.limitMethod = new LimitMethodGenerator(superType);
        this.toStringMethod = new ToStringMethodGenerator(superType);
        this.builderClass = new BuilderClassGenerator(unionName, flyweightName, superType, kindTypeName, resolver);
    }

    public UnionFlyweightGenerator addMember(
        Object value,
        String name,
        AstType type,
        TypeName typeName,
        TypeName unsignedType,
        int size,
        String sizeName,
        AstByteOrder byteOrder)
    {
        memberKindConstant.addMember(value, name);
        memberOffsetConstant.addMember(name);
        memberSizeConstant.addMember(name, typeName, size);
        memberField.addMember(name, typeName, byteOrder);
        memberAccessor.addMember(name, typeName, unsignedType, byteOrder);
        tryWrapMethod.addMember(value, name, typeName, size, sizeName);
        wrapMethod.addMember(value, name, typeName, size, sizeName);
        limitMethod.addMember(value, name, typeName);
        toStringMethod.addMember(value, name, typeName);
        builderClass.addMember(name, type, typeName, size, sizeName, byteOrder);
        return this;
    }

    public UnionFlyweightGenerator addParentMember(
        String name,
        AstType type,
        TypeName typeName,
        AstType unsignedType,
        TypeName unsignedTypeName,
        int size,
        String sizeName,
        TypeName sizeTypeName,
        boolean usedAsSize,
        Object defaultValue,
        AstByteOrder byteOrder)
    {
        memberSizeConstant.addParentMember(name, type, typeName);
        memberOffsetConstant.addParentMember(name, type, typeName);
        memberAccessor.addMember(name, typeName, unsignedTypeName, byteOrder);
        limitMethod.addParentMember(name);
        toStringMethod.addParentMember(name, typeName);
        builderClass.addParentMember(name, type, typeName, unsignedType, unsignedTypeName, size, sizeName, sizeTypeName,
            usedAsSize, defaultValue, byteOrder);
        return this;
    }

    public UnionFlyweightGenerator addMemberAfterParentMember()
    {
        memberSizeConstant.addKindSizeAfterParentMember();
        memberOffsetConstant.addKindOffsetAfterParentMember();
        builderClass.addMemberAfterParentMember();
        return this;
    }

    @Override
    public TypeSpec generate()
    {
        memberKindConstant.build();
        memberOffsetConstant.build();
        memberSizeConstant.build();
        memberField.build();
        kindAccessor.build();
        memberAccessor.build();

        return builder.addMethod(tryWrapMethod.generate())
                      .addMethod(wrapMethod.generate())
                      .addMethod(limitMethod.generate())
                      .addMethod(toStringMethod.generate())
                      .addType(builderClass.generate())
                      .build();
    }

    private static final class KindConstantGenerator extends ClassSpecMixinGenerator
    {
        private final TypeName kindTypeName;

        private KindConstantGenerator(
            ClassName thisType,
            TypeName kindTypeName,
            TypeSpec.Builder builder)
        {
            super(thisType, builder);
            this.kindTypeName = kindTypeName;
        }

        public KindConstantGenerator addMember(
            Object value,
            String name)
        {
            FieldSpec kindConstant = value instanceof String ?
                FieldSpec.builder(enumClassName(kindTypeName), kind(name), PUBLIC, STATIC, FINAL)
                    .initializer("$T.$L", enumClassName(kindTypeName), value)
                    .build() :
                FieldSpec.builder(int.class, kind(name), PUBLIC, STATIC, FINAL)
                    .initializer("$L", value)
                    .build();
            builder.addField(kindConstant);
            return this;
        }
    }

    private static final class MemberSizeConstantGenerator extends ClassSpecMixinGenerator
    {
        private static final Map<TypeName, String> SIZEOF_BY_NAME = initSizeofByName();

        private MemberSizeConstantGenerator(
            ClassName thisType,
            AstType superType,
            TypeName kindTypeName,
            TypeSpec.Builder builder)
        {
            super(thisType, builder);
            if (superType == null && kindTypeName.isPrimitive())
            {
                builder.addField(
                    FieldSpec.builder(int.class, size("kind"), PRIVATE, STATIC, FINAL)
                        .initializer("$T.SIZE_OF_BYTE", BIT_UTIL_TYPE)
                        .build());
            }
        }

        public MemberSizeConstantGenerator addParentMember(
            String name,
            AstType type,
            TypeName typeName)
        {
            if (typeName.isPrimitive())
            {
                builder.addField(
                    FieldSpec.builder(int.class, size(name), PRIVATE, STATIC, FINAL)
                        .initializer("$T.SIZE_OF_$L", BIT_UTIL_TYPE, SIZEOF_BY_NAME.get(typeName))
                        .build());
            }
            return this;
        }

        public MemberSizeConstantGenerator addKindSizeAfterParentMember()
        {
            builder.addField(FieldSpec.builder(int.class, size("kind"), PRIVATE, STATIC, FINAL)
                .initializer("$T.SIZE_OF_BYTE", BIT_UTIL_TYPE)
                .build());
            return this;
        }

        public MemberSizeConstantGenerator addMember(
            String name,
            TypeName typeName,
            int size)
        {
            if (typeName.isPrimitive())
            {
                builder.addField(
                        FieldSpec.builder(int.class, size(name), PRIVATE, STATIC, FINAL)
                                 .initializer("$T.SIZE_OF_$L", BIT_UTIL_TYPE, SIZEOF_BY_NAME.get(typeName))
                                 .build());
            }
            else if (size > 0)
            {
                builder.addField(
                        FieldSpec.builder(int.class, size(name), PRIVATE, STATIC, FINAL)
                                 .initializer("$L", size)
                                 .build());
            }
            return this;
        }

        private static Map<TypeName, String> initSizeofByName()
        {
            Map<TypeName, String> sizeofByName = new HashMap<>();
            sizeofByName.put(TypeName.BOOLEAN, "BOOLEAN");
            sizeofByName.put(TypeName.BYTE, "BYTE");
            sizeofByName.put(TypeName.CHAR, "CHAR");
            sizeofByName.put(TypeName.SHORT, "SHORT");
            sizeofByName.put(TypeName.INT, "INT");
            sizeofByName.put(TypeName.FLOAT, "FLOAT");
            sizeofByName.put(TypeName.LONG, "LONG");
            sizeofByName.put(TypeName.DOUBLE, "DOUBLE");
            return sizeofByName;
        }
    }

    private static final class MemberOffsetConstantGenerator extends ClassSpecMixinGenerator
    {
        private final TypeName kindTypeName;
        private String previousName;

        private MemberOffsetConstantGenerator(
            ClassName thisType,
            AstType superType,
            TypeName kindTypeName,
            TypeSpec.Builder builder)
        {
            super(thisType, builder);
            this.kindTypeName = kindTypeName;
            if (superType == null && kindTypeName.isPrimitive())
            {
                builder.addField(
                    FieldSpec.builder(int.class, offset("kind"), PRIVATE, STATIC, FINAL)
                        .initializer("0")
                        .build());
            }
        }

        public MemberOffsetConstantGenerator addParentMember(
            String name,
            AstType type,
            TypeName typeName)
        {
            String initializer;
            if (previousName == null)
            {
                initializer = "0";
            }
            else
            {
                initializer = String.format("%s + %s", offset(previousName), size(previousName));
            }
            builder.addField(
                FieldSpec.builder(int.class, offset(name), PUBLIC, STATIC, FINAL)
                    .initializer(initializer)
                    .build());

            boolean isFixedSize = typeName.isPrimitive();
            previousName = isFixedSize ? name : null;
            return this;
        }

        public MemberOffsetConstantGenerator addKindOffsetAfterParentMember()
        {
            builder.addField(
                FieldSpec.builder(int.class, offset("kind"), PRIVATE, STATIC, FINAL)
                    .initializer(String.format("%s + %s", offset(previousName), size(previousName)))
                    .build());
            return this;
        }

        public MemberOffsetConstantGenerator addMember(
            String name)
        {
            if (kindTypeName.isPrimitive())
            {
                builder.addField(
                    FieldSpec.builder(int.class, offset(name), PUBLIC, STATIC, FINAL)
                        .initializer(String.format("%s + %s", offset("kind"), size("kind")))
                        .build());
            }
            return this;
        }
    }

    private static final class MemberFieldGenerator extends ClassSpecMixinGenerator
    {
        private MemberFieldGenerator(
            ClassName thisType,
            TypeName kindTypeName,
            TypeSpec.Builder builder)
        {
            super(thisType, builder);
            if (!kindTypeName.isPrimitive())
            {
                builder.addField(FieldSpec.builder(kindTypeName, enumRO(kindTypeName), PRIVATE, FINAL)
                    .initializer("new $T()", kindTypeName)
                    .build());
            }
        }

        public MemberFieldGenerator addMember(
            String name,
            TypeName type,
            AstByteOrder byteOrder)
        {
            if (!type.isPrimitive())
            {
                String fieldRO = String.format("%sRO", name);
                Builder fieldBuilder = FieldSpec.builder(type, fieldRO, PRIVATE);

                builder.addField(fieldBuilder.build());
            }
            return this;
        }
    }

    private static final class KindAccessorGenerator extends ClassSpecMixinGenerator
    {
        private KindAccessorGenerator(
            ClassName thisType,
            TypeName kindTypeName,
            TypeSpec.Builder builder)
        {
            super(thisType, builder);
            MethodSpec.Builder kindMethodBuilder = methodBuilder("kind")
                .addModifiers(PUBLIC);
            if (kindTypeName.isPrimitive())
            {
                kindMethodBuilder.addStatement("return buffer().getByte(offset() + $L) & 0xFF", offset("kind"))
                    .returns(int.class);
            }
            else
            {
                kindMethodBuilder.addStatement("return $L.get()", enumRO(kindTypeName))
                    .returns(enumClassName(kindTypeName));
            }
            builder.addMethod(kindMethodBuilder.build());
        }
    }

    private static final class MemberAccessorGenerator extends ClassSpecMixinGenerator
    {
        private static final Map<TypeName, String> GETTER_NAMES;

        static
        {
            Map<TypeName, String> getterNames = new HashMap<>();
            getterNames.put(TypeName.BYTE, "getByte");
            getterNames.put(TypeName.CHAR, "getChar");
            getterNames.put(TypeName.SHORT, "getShort");
            getterNames.put(TypeName.FLOAT, "getFloat");
            getterNames.put(TypeName.INT, "getInt");
            getterNames.put(TypeName.DOUBLE, "getDouble");
            getterNames.put(TypeName.LONG, "getLong");
            GETTER_NAMES = unmodifiableMap(getterNames);
        }

        private final ClassName visitorRawType;

        private MemberAccessorGenerator(
            ClassName thisType,
            ClassName visitorRawType,
            TypeSpec.Builder builder)
        {
            super(thisType, builder);
            this.visitorRawType = visitorRawType;
        }

        public MemberAccessorGenerator addMember(
            String name,
            TypeName type,
            TypeName unsignedType,
            AstByteOrder byteOrder)
        {
            TypeName publicType = (unsignedType != null) ? unsignedType : type;

            CodeBlock.Builder codeBlock = CodeBlock.builder();

            if (type.isPrimitive())
            {
                String getterName = GETTER_NAMES.get(type);
                if (getterName == null)
                {
                    throw new IllegalStateException("member type not supported: " + type);
                }

                codeBlock.add("$[").add("return ");

                if (publicType != type)
                {
                    codeBlock.add("($T)(", publicType);
                }

                codeBlock.add("buffer().$L(offset() + $L", getterName, offset(name));

                if (publicType != type)
                {
                    if (type == TypeName.BYTE)
                    {
                        codeBlock.add(") & 0xFF)");
                    }
                    else if (type == TypeName.SHORT)
                    {
                        codeBlock.add(", $T.BIG_ENDIAN) & 0xFFFF)", ByteOrder.class);
                    }
                    else if (type == TypeName.INT)
                    {
                        codeBlock.add(", $T.BIG_ENDIAN) & 0xFFFF_FFFF)", ByteOrder.class);
                    }
                    else
                    {
                        codeBlock.add(")");
                    }
                }
                else if (byteOrder == NETWORK)
                {
                    if (type == TypeName.SHORT)
                    {
                        codeBlock.add(", $T.BIG_ENDIAN)", ByteOrder.class);
                    }
                    else if (type == TypeName.INT)
                    {
                        codeBlock.add(", $T.BIG_ENDIAN)", ByteOrder.class);
                    }
                    else
                    {
                        codeBlock.add(")");
                    }
                }
                else
                {
                    codeBlock.add(")");
                }

                codeBlock.add(";\n$]");
            }
            else
            {
                codeBlock.beginControlFlow("if ($LRO == null)", name);

                if (TypeNames.DIRECT_BUFFER_TYPE.equals(type))
                {
                    codeBlock.addStatement("$LRO = new $T(new byte[0])", name, UNSAFE_BUFFER_TYPE);
                }
                else if (type instanceof ParameterizedTypeName)
                {
                    ParameterizedTypeName parameterizedType = (ParameterizedTypeName) type;
                    TypeName typeArgument = parameterizedType.typeArguments.get(0);
                    codeBlock.addStatement("$LRO = new $T(new $T())", name, type, typeArgument);
                }
                else if (type instanceof ClassName && (isString16Type((ClassName) type) ||
                                                           isString32Type((ClassName) type)) && byteOrder == NETWORK)
                {
                    codeBlock.addStatement("$LRO = new $T($T.BIG_ENDIAN)", name, type, ByteOrder.class);
                }
                else
                {
                    codeBlock.addStatement("$LRO = new $T()", name, type);
                }

                codeBlock.endControlFlow();

                if (DIRECT_BUFFER_TYPE.equals(type))
                {
                    TypeVariableName typeVarT = TypeVariableName.get("T");
                    ParameterizedTypeName visitorType = ParameterizedTypeName.get(visitorRawType, typeVarT);

                    builder.addMethod(methodBuilder(name)
                            .addTypeVariable(typeVarT)
                            .addModifiers(PUBLIC)
                            .addParameter(visitorType, "visitor")
                            .returns(typeVarT)
                            .addStatement("return visitor.visit($LRO, 0, $L)", name, size(name))
                            .build());
                }

                codeBlock.addStatement("return $LRO", name);
            }

            builder.addMethod(methodBuilder(name)
                    .addModifiers(PUBLIC)
                    .returns(publicType)
                    .addCode(codeBlock.build())
                    .build());

            return this;
        }
    }

    private final class LimitMethodGenerator extends MethodSpecGenerator
    {
        private final AstType superType;
        private String lastParentMemberName;

        private LimitMethodGenerator(
            AstType superType)
        {
            super(methodBuilder("limit")
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .returns(int.class)
                    .beginControlFlow("switch (kind())"));
            this.superType = superType;
        }

        public LimitMethodGenerator addMember(
            Object value,
            String name,
            TypeName type)
        {
            builder.beginControlFlow("case $L:", value instanceof String ? value.toString() : kind(name));


            if (DIRECT_BUFFER_TYPE.equals(type) || type.isPrimitive())
            {
                builder.addStatement("return offset() + $L + $L", offset(name), size(name));
            }
            else
            {
                builder.addStatement("return $L().limit()", name);
            }

            builder.endControlFlow();

            return this;
        }

        public LimitMethodGenerator addParentMember(
            String name)
        {
            lastParentMemberName = name;
            return this;
        }

        @Override
        public MethodSpec generate()
        {
            builder.beginControlFlow("default:");
            if (superType == null)
            {
                builder.addStatement("return offset()");
            }
            else
            {
                builder.addStatement("return offset() + $L + $L", offset(lastParentMemberName), size(lastParentMemberName));
            }
            return builder.endControlFlow()
                .endControlFlow()
                .build();
        }

    }

    private final class TryWrapMethodGenerator extends MethodSpecGenerator
    {
        private final TypeName kindTypeName;

        private TryWrapMethodGenerator(
            TypeName kindTypeName)
        {
            super(methodBuilder("tryWrap")
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .addParameter(DIRECT_BUFFER_TYPE, "buffer")
                    .addParameter(int.class, "offset")
                    .addParameter(int.class, "maxLimit")
                    .returns(thisName));
            this.kindTypeName = kindTypeName;
            addFailIfStatement("super.tryWrap(buffer, offset, maxLimit) == null");
            if (!kindTypeName.isPrimitive())
            {
                builder.addStatement("final $T $L = $L.tryWrap(buffer, offset, maxLimit)", kindTypeName, fieldName(kindTypeName),
                    enumRO(kindTypeName));
                addFailIfStatement(String.format("%s == null", fieldName(kindTypeName)));
            }
            builder.beginControlFlow("switch (kind())");
        }

        public TryWrapMethodGenerator addMember(
            Object value,
            String name,
            TypeName type,
            int size,
            String sizeName)
        {
            builder.beginControlFlow("case $L:", value instanceof String ? value.toString() : kind(name));

            if (DIRECT_BUFFER_TYPE.equals(type))
            {
                addFailIfStatement("$L().tryWrap(buffer, offset + $L, $L) == null",
                        name, offset(name), size(name));
            }
            else if (!type.isPrimitive())
            {
                if (size >= 0)
                {
                    addFailIfStatement("$L().tryWrap(buffer, offset + $L, offset + $L + $L) == null",
                            name, offset(name), offset(name), size);
                }
                else if (sizeName != null)
                {
                    addFailIfStatement("$L().tryWrap(buffer, offset + $L, offset + $L + $L()) == null",
                            name, offset(name), offset(name), sizeName);
                }
                else if (!kindTypeName.isPrimitive())
                {
                    addFailIfStatement("$L().tryWrap(buffer, offset + $L.sizeof(), maxLimit) == null", name,
                        fieldName(kindTypeName));
                }
                else
                {
                    addFailIfStatement("$L().tryWrap(buffer, offset + $L, maxLimit) == null", name, offset(name));
                }
            }
            builder.addStatement("break").endControlFlow();
            return this;
        }

        @Override
        public MethodSpec generate()
        {
            return builder.beginControlFlow("default:")
                          .addStatement("break")
                          .endControlFlow()
                          .endControlFlow()
                          .beginControlFlow("if (limit() > maxLimit)")
                          .addStatement("return null")
                          .endControlFlow()
                          .addStatement("return this")
                          .build();
        }

        private void addFailIfStatement(
            String string,
            Object... args)
        {
            builder.beginControlFlow("if (" + string + ")", args);
            builder.addStatement("return null");
            builder.endControlFlow();
        }
    }

    private final class WrapMethodGenerator extends MethodSpecGenerator
    {
        private final TypeName kindTypeName;

        private WrapMethodGenerator(
            TypeName kindTypeName)
        {
            super(methodBuilder("wrap")
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .addParameter(DIRECT_BUFFER_TYPE, "buffer")
                    .addParameter(int.class, "offset")
                    .addParameter(int.class, "maxLimit")
                    .returns(thisName)
                    .addStatement("super.wrap(buffer, offset, maxLimit)"));
            this.kindTypeName = kindTypeName;
            if (!kindTypeName.isPrimitive())
            {
                builder.addStatement("final $T $L = $L.wrap(buffer, offset, maxLimit)", kindTypeName, fieldName(kindTypeName),
                    enumRO(kindTypeName));
            }
            builder.beginControlFlow("switch (kind())");
        }

        public WrapMethodGenerator addMember(
            Object value,
            String name,
            TypeName type,
            int size,
            String sizeName)
        {
            builder.beginControlFlow("case $L:", value instanceof String ? value.toString() : kind(name));

            if (DIRECT_BUFFER_TYPE.equals(type))
            {
                builder.addStatement("$L().wrap(buffer, offset + $L, $L)",
                        name, offset(name), size(name));
            }
            else if (!type.isPrimitive())
            {
                if (size >= 0)
                {
                    builder.addStatement("$L().wrap(buffer, offset + $L, offset + $L + $L)",
                            name, offset(name), offset(name), size);
                }
                else if (sizeName != null)
                {
                    builder.addStatement("$L().wrap(buffer, offset + $L, offset + $L + $L())",
                            name, offset(name), offset(name), sizeName);
                }
                else if (!kindTypeName.isPrimitive())
                {
                    builder.addStatement("$L().wrap(buffer, offset + $L.sizeof(), maxLimit)", name,
                        fieldName(kindTypeName));
                }
                else
                {
                    builder.addStatement("$L().wrap(buffer, offset + $L, maxLimit)", name, offset(name));
                }
            }
            builder.addStatement("break").endControlFlow();
            return this;
        }

        @Override
        public MethodSpec generate()
        {
            return builder.beginControlFlow("default:")
                          .addStatement("break")
                          .endControlFlow()
                          .endControlFlow()
                          .addStatement("checkLimit(limit(), maxLimit)")
                          .addStatement("return this")
                          .build();
        }
    }

    private final class ToStringMethodGenerator extends MethodSpecGenerator
    {
        private final AstType superType;
        private final StringBuilder fieldStringBuilder = new StringBuilder();
        private final StringBuilder fieldMethodBuilder = new StringBuilder();
        private String lastParentMemberName;

        private ToStringMethodGenerator(
            AstType superType)
        {
            super(methodBuilder("toString")
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .returns(String.class)
                    .beginControlFlow("switch (kind())"));
            this.superType = superType;
        }

        public ToStringMethodGenerator addMember(
            Object value,
            String name,
            TypeName typeName)
        {
            String fieldString = fieldStringBuilder.toString();
            String fieldMethod = fieldMethodBuilder.toString();
            builder.beginControlFlow("case $L:", value instanceof String ? value.toString() : kind(name));

            if (typeName instanceof ClassName && isStringType((ClassName) typeName))
            {
                builder.addStatement("return String.format(\"$L [$L$L=%s]\", $L$L().asString())", constant(baseName),
                    fieldString, name, fieldMethod, name);
            }
            else if (typeName.isPrimitive())
            {
                builder.addStatement("return String.format(\"$L [$L$L=%d]\", $L$L())", constant(baseName), fieldString,
                    name, fieldMethod, name);
            }
            else
            {
                builder.addStatement("return String.format(\"$L [$L$L=%s]\", $L$L())", constant(baseName), fieldString,
                    name, fieldMethod, name);
            }
            builder.endControlFlow();
            return this;
        }

        public ToStringMethodGenerator addParentMember(
            String name,
            TypeName typeName)
        {
            if (typeName.isPrimitive())
            {
                fieldStringBuilder.append(String.format("%s=%%d, ", name));
                fieldMethodBuilder.append(String.format("%s(), ", name));
            }
            else if (typeName instanceof ClassName && isStringType((ClassName) typeName))
            {
                fieldMethodBuilder.append(String.format("%sRO.asString(), ", name));
            }
            else
            {
                fieldStringBuilder.append(String.format("%s=%%s, ", name));
                fieldMethodBuilder.append(String.format("%s(), ", name));
            }
            lastParentMemberName = name;

            return this;
        }

        @Override
        public MethodSpec generate()
        {
            builder.beginControlFlow("default:");
            if (superType == null)
            {
                builder.addStatement("return String.format(\"$L [unknown]\")", constant(baseName));
            }
            else
            {
                builder.addStatement("return String.format(\"$L [$L=%d]\", $L())", constant(baseName), lastParentMemberName,
                    lastParentMemberName);
            }
            return builder.endControlFlow()
                .endControlFlow()
                .build();
        }
    }

    private static final class BuilderClassGenerator extends ClassSpecGenerator
    {
        private final TypeSpec.Builder builder;
        private final ClassName unionType;
        private final MemberConstantGenerator memberConstant;
        private final MemberFieldGenerator memberField;
        private final MemberAccessorGenerator memberAccessor;
        private final MemberMutatorGenerator memberMutator;
        private final WrapMethodGenerator wrapMethod;
        private final BuildMethodGenerator buildMethod;
        private String priorFieldIfDefaulted;
        private boolean priorDefaultedIsPrimitive;
        private Object priorDefaultValue;
        private String priorSizeName;
        private TypeName priorSizeType;

        private BuilderClassGenerator(
            ClassName unionType,
            ClassName flyweightType,
            AstType superType,
            TypeName kindTypeName,
            TypeResolver resolver)
        {
            this(unionType.nestedClass("Builder"), flyweightType.nestedClass("Builder"), unionType, superType, kindTypeName,
                resolver);
        }

        private BuilderClassGenerator(
            ClassName thisType,
            ClassName builderRawType,
            ClassName unionType,
            AstType superType,
            TypeName kindTypeName,
            TypeResolver resolver)
        {
            super(thisType);
            this.builder = classBuilder(thisType.simpleName())
                    .addModifiers(PUBLIC, STATIC, FINAL)
                    .superclass(ParameterizedTypeName.get(builderRawType, unionType));
            this.unionType = unionType;
            this.wrapMethod = new WrapMethodGenerator(superType);
            this.memberConstant = new MemberConstantGenerator(thisType, superType, builder);
            this.memberField = new MemberFieldGenerator(thisType, kindTypeName, builder);
            this.memberAccessor = new MemberAccessorGenerator(thisType, kindTypeName, builder);
            this.memberMutator = new MemberMutatorGenerator(thisType, superType, kindTypeName, builder, resolver);
            this.buildMethod = new BuildMethodGenerator(unionType, superType, builder);
        }

        private void addMember(
            String name,
            AstType type,
            TypeName typeName,
            int size,
            String sizeName,
            AstByteOrder byteOrder)
        {
            // TODO: eliminate need for lookahead
            memberMutator.lookaheadMember(name, typeName);

            memberField.addMember(name, typeName, byteOrder);
            memberAccessor.addMember(name, typeName, size, sizeName, byteOrder);
            memberMutator.addMember(name, type, typeName, sizeName, byteOrder);

            //   setMethod
        }

        private void addParentMember(
            String name,
            AstType type,
            TypeName typeName,
            AstType unsignedType,
            TypeName unsignedTypeName,
            int size,
            String sizeName,
            TypeName sizeTypeName,
            boolean usedAsSize,
            Object defaultValue,
            AstByteOrder byteOrder)
        {
            Consumer<CodeBlock.Builder> defaultPriorField = priorFieldIfDefaulted == null ? null : this::defaultPriorField;
            memberConstant.addParentMember(name);
            memberMutator.addParentMember(name, type, typeName, unsignedType, unsignedTypeName,
                usedAsSize, byteOrder, priorFieldIfDefaulted, defaultPriorField);
            buildMethod.addParentMember(name);
            if (defaultValue != null)
            {
                priorFieldIfDefaulted = name;
                priorDefaultedIsPrimitive = typeName.isPrimitive() || isVarintType(typeName) || isVaruintType(typeName);
                priorDefaultValue = defaultValue;
                priorSizeName = sizeName;
                priorSizeType = sizeTypeName;
            }
            else
            {
                priorFieldIfDefaulted = null;
            }
        }

        private void addMemberAfterParentMember()
        {
            memberMutator.addKindAfterParentMember();
        }

        @Override
        public TypeSpec generate()
        {
            memberField.build();
            memberAccessor.build();
            memberMutator.build();
            memberConstant.build();
            buildMethod.build();
            return builder.addMethod(constructor())
                          .addMethod(wrapMethod.generate())
                          .build();
        }

        private MethodSpec constructor()
        {
            return constructorBuilder()
                    .addModifiers(PUBLIC)
                    .addStatement("super(new $T())", unionType)
                    .build();
        }

        private static final class MemberConstantGenerator extends ClassSpecMixinGenerator
        {
            private final AstType superType;
            private int nextIndex;

            private MemberConstantGenerator(
                ClassName thisType,
                AstType superType,
                TypeSpec.Builder builder)
            {
                super(thisType, builder);
                this.superType = superType;
            }

            public MemberConstantGenerator addParentMember(
                String name)
            {
                builder.addField(
                    FieldSpec.builder(int.class, index(name), PRIVATE, STATIC, FINAL)
                        .initializer(Integer.toString(nextIndex++))
                        .build());
                return this;
            }

            @Override
            public TypeSpec.Builder build()
            {
                if (superType != null)
                {
                    builder.addField(
                        FieldSpec.builder(int.class, "lastFieldSet", PRIVATE)
                            .initializer("-1")
                            .build());
                }
                return super.build();
            }
        }

        private static final class MemberFieldGenerator extends ClassSpecMixinGenerator
        {
            private  MemberFieldGenerator(
                ClassName thisType,
                TypeName kindTypeName,
                TypeSpec.Builder builder)
            {
                super(thisType, builder);
                if (!kindTypeName.isPrimitive())
                {
                    TypeName builderType = ((ClassName) kindTypeName).nestedClass("Builder");
                    builder.addField(FieldSpec.builder(builderType, enumRW(kindTypeName), PRIVATE, FINAL)
                        .initializer("new $T()", builderType)
                        .build());
                }
            }

            public MemberFieldGenerator addMember(
                String name,
                TypeName type,
                AstByteOrder byteOrder)
            {
                if (!type.isPrimitive())
                {
                    String fieldRW = String.format("%sRW", name);

                    if (TypeNames.DIRECT_BUFFER_TYPE.equals(type))
                    {
                        builder.addField(FieldSpec.builder(MUTABLE_DIRECT_BUFFER_TYPE, fieldRW, PRIVATE)
                                .build());
                    }
                    else if (type instanceof ParameterizedTypeName)
                    {
                        ParameterizedTypeName parameterizedType = (ParameterizedTypeName) type;
                        ClassName rawType = parameterizedType.rawType;
                        ClassName itemType = (ClassName) parameterizedType.typeArguments.get(0);
                        ClassName builderRawType = rawType.nestedClass("Builder");
                        ClassName itemBuilderType = itemType.nestedClass("Builder");
                        ParameterizedTypeName builderType = ParameterizedTypeName.get(builderRawType, itemBuilderType, itemType);

                        builder.addField(FieldSpec.builder(builderType, fieldRW, PRIVATE)
                                .build());
                    }
                    else if (type instanceof ClassName)
                    {
                        ClassName classType = (ClassName) type;
                        TypeName builderType = classType.nestedClass("Builder");

                        if ((isString16Type(classType) || isString32Type(classType)) && byteOrder == NETWORK)
                        {
                            builder.addField(FieldSpec.builder(builderType, fieldRW, PRIVATE)
                                    .build());
                        }
                        else
                        {
                            builder.addField(FieldSpec.builder(builderType, fieldRW, PRIVATE)
                                    .build());
                        }
                    }
                    else
                    {
                        throw new IllegalArgumentException("Unsupported member type: " + type);
                    }
                }
                return this;
            }
        }

        private static final class MemberAccessorGenerator extends ClassSpecMixinGenerator
        {
            private final TypeName kindTypeName;

            private MemberAccessorGenerator(
                ClassName thisType,
                TypeName kindTypeName,
                TypeSpec.Builder builder)
            {
                super(thisType, builder);
                this.kindTypeName = kindTypeName;
            }

            public MemberAccessorGenerator addMember(
                String name,
                TypeName type,
                int size,
                String sizeName,
                AstByteOrder byteOrder)
            {
                if (!type.isPrimitive())
                {
                    if (TypeNames.DIRECT_BUFFER_TYPE.equals(type))
                    {
                        String limit = (size >= 0) ? "offset() + " + offset(name) + " + " + size(name) :
                            sizeName != null ? sizeName + "()" : "maxLimit()";

                        builder.addMethod(methodBuilder(name)
                                .addModifiers(PRIVATE)
                                .beginControlFlow("if ($LRW == null)", name)
                                    .addStatement("$LRW = new $T(new byte[0])", name, UNSAFE_BUFFER_TYPE)
                                .endControlFlow()
                                .addStatement("$LRW.wrap(buffer(), offset() + $L, $L)", name, offset(name), limit)
                                .addStatement("return $LRW", name)
                                .returns(MUTABLE_DIRECT_BUFFER_TYPE)
                                .build());
                    }
                    else if (type instanceof ParameterizedTypeName)
                    {
                        ParameterizedTypeName parameterizedType = (ParameterizedTypeName) type;
                        ClassName rawType = parameterizedType.rawType;
                        ClassName itemType = (ClassName) parameterizedType.typeArguments.get(0);
                        ClassName builderRawType = rawType.nestedClass("Builder");
                        ClassName itemBuilderType = itemType.nestedClass("Builder");
                        ParameterizedTypeName builderType = ParameterizedTypeName.get(builderRawType, itemBuilderType, itemType);

                        builder.addMethod(methodBuilder(name)
                                .addModifiers(PRIVATE)
                                .addParameter(int.class, "offset")
                                .beginControlFlow("if ($LRW == null)", name)
                                    .addStatement("$LRW = new $T(new $T(), new $T())",
                                        name, builderType, itemBuilderType, itemType)
                                .endControlFlow()
                                .addStatement("return $LRW.wrap(buffer(), offset, maxLimit())", name)
                                .returns(builderType)
                                .build());
                    }
                    else if (type instanceof ClassName)
                    {
                        ClassName classType = (ClassName) type;
                        TypeName builderType = classType.nestedClass("Builder");
                        String limit = (size >= 0) ? "offset() + " + offset(name) + " + " + size(name) :
                            sizeName != null ? sizeName + "()" : "maxLimit()";

                        MethodSpec.Builder accessorBuilder = methodBuilder(name).addModifiers(PRIVATE).returns(builderType);

                        if (kindTypeName.isPrimitive())
                        {
                            accessorBuilder
                                .addStatement("int newLimit = $L", limit)
                                .addStatement("checkLimit(newLimit, maxLimit())");
                        }

                        if ((isString16Type(classType) || isString32Type(classType)) && byteOrder == NETWORK)
                        {
                            accessorBuilder.beginControlFlow("if ($LRW == null)", name)
                                            .addStatement("$LRW = new $T($T.BIG_ENDIAN)", name, builderType, ByteOrder.class)
                                           .endControlFlow();
                        }
                        else
                        {
                            accessorBuilder.beginControlFlow("if ($LRW == null)", name)
                                            .addStatement("$LRW = new $T()", name, builderType)
                                           .endControlFlow();
                        }

                        if (kindTypeName.isPrimitive())
                        {
                            accessorBuilder
                            .addStatement("return $LRW.wrap(buffer(), offset() + $L, newLimit)", name, offset(name));
                        }
                        else
                        {
                            accessorBuilder.addStatement("return $LRW.wrap(buffer(), offset() + $L.sizeof(), maxLimit())",
                                name, enumRW(kindTypeName));
                        }
                        builder.addMethod(accessorBuilder.build());

                        if (sizeName != null)
                        {
                            builder.addMethod(methodBuilder(name)
                                    .addModifiers(PRIVATE)
                                    .addParameter(int.class, "limit")
                                    .addStatement("return $LRW.wrap(buffer(), offset() + $L, limit)", name, offset(name))
                                    .returns(builderType)
                                    .build());
                        }
                    }
                    else
                    {
                        throw new IllegalArgumentException("Unsupported member type: " + type);
                    }
                }

                return this;
            }
        }

        private static final class MemberMutatorGenerator extends ClassSpecMixinGenerator
        {
            private static final Map<TypeName, String> PUTTER_NAMES;
            private static final Map<TypeName, String[]> UNSIGNED_INT_RANGES;

            static
            {
                Map<TypeName, String> putterNames = new HashMap<>();
                putterNames.put(TypeName.BYTE, "putByte");
                putterNames.put(TypeName.CHAR, "putChar");
                putterNames.put(TypeName.SHORT, "putShort");
                putterNames.put(TypeName.FLOAT, "putFloat");
                putterNames.put(TypeName.INT, "putInt");
                putterNames.put(TypeName.DOUBLE, "putDouble");
                putterNames.put(TypeName.LONG, "putLong");
                PUTTER_NAMES = unmodifiableMap(putterNames);

                Map<TypeName, String[]> unsigned = new HashMap<>();
                unsigned.put(TypeName.BYTE, new String[]{"0", "0XFF"});
                unsigned.put(TypeName.SHORT, new String[]{"0", "0xFFFF"});
                unsigned.put(TypeName.INT, new String[]{"0", "0xFFFFFFFFL"});
                unsigned.put(TypeName.LONG, new String[]{"0L", null});
                UNSIGNED_INT_RANGES = unmodifiableMap(unsigned);
            }

            private final TypeResolver resolver;

            private boolean priorFieldIsAutomaticallySet;
            private String nextName;
            private TypeName nextType;

            private String deferredName;
            private AstType deferredType;
            private TypeName deferredTypeName;
            private String deferredSizeName;
            private AstByteOrder deferredByteOrder;

            private String lastParentMemberName;

            private MemberMutatorGenerator(
                ClassName thisType,
                AstType superType,
                TypeName kindTypeName,
                TypeSpec.Builder builder,
                TypeResolver resolver)
            {
                super(thisType, builder);
                this.resolver = resolver;
                if (superType == null)
                {
                    MethodSpec.Builder kindMethodBuilder = MethodSpec.methodBuilder("kind")
                        .addModifiers(PUBLIC)
                        .returns(thisType);
                    if (kindTypeName.isPrimitive())
                    {
                        kindMethodBuilder.addParameter(int.class, "value")
                            .addStatement("buffer().putByte(offset() + $L, (byte)(value & 0xFF))", offset("kind"));
                    }
                    else
                    {
                        kindMethodBuilder.addParameter(enumClassName(kindTypeName), "value")
                            .addStatement("$L.wrap(buffer(), offset(), maxLimit())", enumRW(kindTypeName))
                            .addStatement("$L.set(value)", enumRW(kindTypeName))
                            .addStatement("limit($L.build().limit())", enumRW(kindTypeName));
                    }
                    kindMethodBuilder.addStatement("return this");
                    builder.addMethod(kindMethodBuilder.build());
                }
            }

            public void lookaheadMember(
                String name,
                TypeName type)
            {
                nextName = name;
                nextType = type;
                addDeferredMemberIfNecessary();
            }

            public MemberMutatorGenerator addMember(
                String name,
                AstType type,
                TypeName typeName,
                String sizeName,
                AstByteOrder byteOrder)
            {
                deferredName = name;
                deferredType = type;
                deferredTypeName = typeName;
                deferredSizeName = sizeName;
                deferredByteOrder = byteOrder;
                return this;
            }

            public MemberMutatorGenerator addParentMember(
                String name,
                AstType type,
                TypeName typeName,
                AstType unsignedType,
                TypeName unsignedTypeName,
                boolean usedAsSize,
                AstByteOrder byteOrder,
                String priorFieldIfDefaulted,
                Consumer<CodeBlock.Builder> defaultPriorField)
            {
                boolean automaticallySet = usedAsSize && !isVarintType(typeName) && !isVaruintType(typeName);
                if (typeName.isPrimitive())
                {
                    addPrimitiveParentMember(name, type, typeName, unsignedType, unsignedTypeName, usedAsSize, byteOrder,
                        priorFieldIfDefaulted, defaultPriorField);
                }
                priorFieldIsAutomaticallySet = automaticallySet;
                lastParentMemberName = name;
                return this;
            }

            private MemberMutatorGenerator addKindAfterParentMember()
            {
                builder.addMethod(MethodSpec.methodBuilder("kind")
                    .addModifiers(PUBLIC)
                    .addParameter(int.class, "value")
                    .returns(thisType)
                    .addStatement("assert lastFieldSet == $L : \"Field \\\"$L\\\" is not set\"", index(lastParentMemberName),
                        lastParentMemberName)
                    .addStatement("buffer().putByte(offset() + $L, (byte)(value & 0xFF))", offset("kind"))
                    .addStatement("return this")
                    .build());
                return this;
            }

            private void addPrimitiveParentMember(
                String name,
                AstType type,
                TypeName typeName,
                AstType unsignedType,
                TypeName unsignedTypeName,
                boolean usedAsSize,
                AstByteOrder byteOrder,
                String priorFieldIfDefaulted,
                Consumer<CodeBlock.Builder> defaultPriorField)
            {

                boolean automaticallySet = usedAsSize && !isVarintType(typeName) && !isVaruintType(typeName);
                String putterName = PUTTER_NAMES.get(typeName);
                if (putterName == null)
                {
                    throw new IllegalStateException("member type not supported: " + typeName);
                }

                TypeName generateTypeName = (unsignedTypeName != null) ? unsignedTypeName : typeName;
                CodeBlock.Builder code = CodeBlock.builder();
                if (unsignedTypeName != null)
                {
                    generateUnsignedIntRangeCheck(name, typeName, code);
                }

                if (priorFieldIfDefaulted != null)
                {
                    generateDefaultPriorField(priorFieldIfDefaulted, defaultPriorField, code);
                }
                if (!automaticallySet)
                {
                    code.addStatement("assert lastFieldSet == $L - 1", index(name));
                }
                code.addStatement("int newLimit = limit() + $L", size(name))
                    .addStatement("checkLimit(newLimit, maxLimit())");

                if (type.bits() == 24)
                {
                    if (byteOrder == NETWORK)
                    {
                        code.addStatement("buffer().putByte(limit(), (byte) (value >> 16))");
                        code.addStatement("buffer().putByte(limit() + 1, (byte) (value >> 8))");
                        code.addStatement("buffer().putByte(limit() + 2, (byte) value)");
                    }
                    else
                    {
                        code.beginControlFlow("if ($T.NATIVE_BYTE_ORDER == $T.BIG_ENDIAN)", BUFFER_UTIL_TYPE, ByteOrder.class);
                        code.addStatement("buffer().putByte(limit(), (byte) (value >> 16))");
                        code.addStatement("buffer().putByte(limit() + 1, (byte) (value >> 8))");
                        code.addStatement("buffer().putByte(limit() + 2, (byte) value)");
                        code.nextControlFlow("else");
                        code.addStatement("buffer().putByte(limit(), (byte) value)");
                        code.addStatement("buffer().putByte(limit() + 1, (byte) (value >> 8))");
                        code.addStatement("buffer().putByte(limit() + 2, (byte) (value >> 16))");
                        code.endControlFlow();
                    }
                }
                else
                {
                    code.add("$[")
                        .add("buffer().$L(limit(), ", putterName);
                    if (generateTypeName != typeName)
                    {
                        code.add("($T)", typeName);

                        switch (type.bits())
                        {
                        case 8:
                            code.add("(value & 0xFF)");
                            break;
                        case 16:
                            code.add("(value & 0xFFFF)");
                            break;
                        case 24:
                            code.add("(value & 0x00FF_FFFF)");
                            break;
                        case 32:
                            code.add("(value & 0xFFFF_FFFFL)");
                            break;
                        default:
                            code.add("value");
                            break;
                        }
                    }
                    else
                    {
                        code.add("value");
                    }
                    if (byteOrder == NETWORK)
                    {
                        if (typeName == TypeName.SHORT || typeName == TypeName.INT || typeName == TypeName.LONG)
                        {
                            code.add(", $T.BIG_ENDIAN", ByteOrder.class);
                        }
                    }
                    code.add(");\n$]");
                }

                if (usedAsSize)
                {
                    code.addStatement("$L = limit()", dynamicOffset(name));
                }
                if (!automaticallySet)
                {
                    code.addStatement("lastFieldSet = $L", index(name));
                }
                code.addStatement("limit(newLimit)")
                    .addStatement("return this");

                builder.addMethod(methodBuilder(methodName(name))
                    .addModifiers(usedAsSize ? PRIVATE : PUBLIC)
                    .addParameter(generateTypeName, "value")
                    .returns(thisType)
                    .addCode(code.build())
                    .build());
            }

            private void generateUnsignedIntRangeCheck(String name, TypeName typeName, CodeBlock.Builder code)
            {
                String[] range = UNSIGNED_INT_RANGES.get(typeName);
                code.beginControlFlow("if (value < $L)", range[0])
                    .addStatement("throw new IllegalArgumentException(String.format($S, value))",
                        format("Value %%d too low for field \"%s\"", name))
                    .endControlFlow();
                if (range[1] != null)
                {
                    code.beginControlFlow("if (value > $L)", range[1])
                        .addStatement("throw new IllegalArgumentException(String.format($S, value))",
                            format("Value %%d too high for field \"%s\"", name))
                        .endControlFlow();
                }
            }

            private void generateDefaultPriorField(
                String priorFieldIfDefaulted,
                Consumer<CodeBlock.Builder> defaultPriorField,
                CodeBlock.Builder code)
            {
                if (priorFieldIsAutomaticallySet)
                {
                    code.beginControlFlow("if ($L == -1)", dynamicOffset(priorFieldIfDefaulted));
                }
                else
                {
                    code.beginControlFlow("if (lastFieldSet < $L)", index(priorFieldIfDefaulted));
                }
                defaultPriorField.accept(code);
                code.endControlFlow();
            }


            private static String dynamicOffset(
                String fieldName)
            {
                return String.format("dynamicOffset%s", initCap(fieldName));
            }

            @Override
            public TypeSpec.Builder build()
            {
                addDeferredMemberIfNecessary();
                return super.build();
            }

            private MemberMutatorGenerator addDeferredMemberIfNecessary()
            {
                if (deferredName == null || deferredTypeName == null)
                {
                    return this;
                }

                String name = deferredName;
                TypeName type = deferredTypeName;
                String sizeName = deferredSizeName;
                AstByteOrder byteOrder = deferredByteOrder;

                deferredName = null;
                deferredTypeName = null;
                deferredSizeName = null;
                deferredByteOrder = null;

                if (type.isPrimitive())
                {
                    addPrimitiveMember(name, type, byteOrder);
                }
                else
                {
                    addNonPrimitiveMember(name, type, sizeName);
                }

                return this;
            }

            private void addPrimitiveMember(
                String name,
                TypeName type,
                AstByteOrder byteOrder)
            {
                String putterName = PUTTER_NAMES.get(type);
                if (putterName == null)
                {
                    throw new IllegalStateException("member type not supported: " + type);
                }

                String statement = byteOrder == NETWORK && (type == TypeName.SHORT || type == TypeName.INT)
                    ? String.format("buffer().%s(offset() + $L, value, ByteOrder.BIG_ENDIAN)", putterName)
                    : String.format("buffer().%s(offset() + $L, value)", putterName);

                CodeBlock.Builder code = CodeBlock.builder()
                    .addStatement("kind($L)", kind(name))
                    .addStatement(statement, offset(name));

                if (nextType instanceof ParameterizedTypeName)
                {
                    code.addStatement("$L(offset() + $L + $L)", nextName, offset(name), size(name));
                }

                code.addStatement("limit(offset() + $L + $L)", offset(name), size(name));
                code.addStatement("return this");

                builder.addMethod(methodBuilder(name)
                        .addModifiers(PUBLIC)
                        .addParameter(type, "value")
                        .returns(thisType)
                        .addCode(code.build())
                        .build());
            }

            private void addNonPrimitiveMember(
                String name,
                TypeName type,
                String sizeName)
            {
                if (type instanceof ClassName)
                {
                    ClassName className = (ClassName) type;
                    addClassType(name, className, sizeName);
                }
                else if (type instanceof ParameterizedTypeName)
                {
                    ParameterizedTypeName parameterizedType = (ParameterizedTypeName) type;
                    addParameterizedType(name, parameterizedType);
                }
                else
                {
                    builder.addMethod(methodBuilder(name)
                            .addModifiers(PUBLIC)
                            .returns(thisType)
                            .addParameter(type, "value")
                            .addStatement("kind($L)", kind(name))
                            .addStatement("$L().set(value)", name)
                            .addStatement("return this")
                            .build());
                }
            }

            private void addClassType(
                String name,
                ClassName className,
                String sizeName)
            {
                AstNamedNode kindNode = resolver.resolve(deferredType.name());
                if ("String8FW".equals(className.simpleName()) ||
                    "String16FW".equals(className.simpleName()) ||
                    "String32FW".equals(className.simpleName()))
                {
                    CodeBlock.Builder codeBlock = CodeBlock.builder();
                    ClassName builderType = className.nestedClass("Builder");

                    // TODO: handle optional fields
                    codeBlock
                        .addStatement("kind($L)", kind(name))
                        .addStatement("$T $L = $L()", builderType, name, name)
                        .addStatement("$L.set(value, $T.UTF_8)", name, StandardCharsets.class);
                    if (nextType instanceof ParameterizedTypeName)
                    {
                        codeBlock.addStatement("$L($L.build().limit())", nextName, name);
                    }
                    codeBlock.addStatement("limit($L.build().limit())", name)
                        .addStatement("return this");
                    builder.addMethod(methodBuilder(name)
                            .addModifiers(PUBLIC)
                            .returns(thisType)
                            .addParameter(String.class, "value")
                            .addCode(codeBlock.build())
                            .build());

                    codeBlock = CodeBlock.builder();
                    codeBlock
                        .addStatement("kind($L)", kind(name))
                        .addStatement("$T $L = $L()", builderType, name, name)
                        .addStatement("$L.set(value)", name);
                    if (nextType instanceof ParameterizedTypeName)
                    {
                        codeBlock.addStatement("$L($L.build().limit())", nextName, name);
                    }
                    codeBlock.addStatement("limit($L.build().limit())", name)
                        .addStatement("return this");

                    builder.addMethod(methodBuilder(name)
                        .addModifiers(PUBLIC)
                        .returns(thisType)
                        .addParameter(resolver.resolveType(AstType.STRING), "value")
                        .addCode(codeBlock.build())
                        .build());
                }
                else if (DIRECT_BUFFER_TYPE.equals(className))
                {
                    addDirectBufferType(name);
                }
                else if (kindNode != null && kindNode.getKind() == Kind.LIST)
                {
                    ClassName builderType = className.nestedClass("Builder");
                    CodeBlock.Builder code = CodeBlock.builder()
                        .addStatement("final int fieldCount = value.fieldCount()")
                        .addStatement("final $T fields = value.fields()", DIRECT_BUFFER_TYPE)
                        .addStatement("final int length = fields.capacity()")
                        .addStatement("kind($L)", kind(name))
                        .addStatement("$T $L = $L()", builderType, name, name)
                        .addStatement("$L.fields(fieldCount, fields, 0, length)", name)
                        .addStatement("limit($L.build().limit())", name)
                        .addStatement("return this");

                    builder.addMethod(methodBuilder(name)
                        .addModifiers(PUBLIC)
                        .returns(thisType)
                        .addParameter(className, "value")
                        .addCode(code.build())
                        .build());
                }
                else
                {
                    ClassName consumerType = ClassName.get(Consumer.class);
                    ClassName builderType = className.nestedClass("Builder");
                    TypeName mutatorType = ParameterizedTypeName.get(consumerType, builderType);
                    CodeBlock.Builder code = CodeBlock.builder()
                        .addStatement("kind($L)", kind(name));

                    if (sizeName != null)
                    {
                        code.addStatement("$T $L = $L(maxLimit())", builderType, name, name);
                    }
                    else
                    {
                        code.addStatement("$T $L = $L()", builderType, name, name);
                    }
                    code.addStatement("mutator.accept($L)", name)
                        .addStatement("limit($L.build().limit())", name)
                        .addStatement("return this");
                    builder.addMethod(methodBuilder(name)
                            .addModifiers(PUBLIC)
                            .returns(thisType)
                            .addParameter(mutatorType, "mutator")
                            .addCode(code.build())
                            .build());

                    code = CodeBlock.builder();
                    code.addStatement("kind($L)", kind(name))
                        .addStatement("int newLimit = limit() + field.sizeof()")
                        .addStatement("checkLimit(newLimit, maxLimit())")
                        .addStatement("buffer().putBytes(limit(), field.buffer(), field.offset(), field.sizeof())")
                        .addStatement("limit(newLimit)")
                        .addStatement("return this");
                    builder.addMethod(methodBuilder(methodName(name))
                        .addModifiers(PUBLIC)
                        .returns(thisType)
                        .addParameter(className, "field")
                        .addCode(code.build())
                        .build());
                }
            }

            private void addDirectBufferType(
                String name)
            {
                TypeVariableName typeVarT = TypeVariableName.get("T");
                ClassName biconsumerRawType = ClassName.get(BiConsumer.class);
                ParameterizedTypeName consumerType = ParameterizedTypeName.get(Consumer.class, byte[].class);
                ParameterizedTypeName converterType = ParameterizedTypeName.get(biconsumerRawType, typeVarT, consumerType);

                builder.addMethod(methodBuilder(name)
                        .addModifiers(PUBLIC)
                        .addTypeVariable(typeVarT)
                        .addParameter(typeVarT, "value")
                        .addParameter(converterType, "converter")
                        .returns(thisType)
                        .addStatement("converter.accept(value, this::$L)", name)
                        .addStatement("return this")
                        .build());

                builder.addMethod(methodBuilder(name)
                        .addModifiers(PUBLIC)
                        .addParameter(DIRECT_BUFFER_TYPE, "value")
                        .addParameter(int.class, "offset")
                        .addParameter(int.class, "length")
                        .returns(thisType)
                        .addStatement("kind($L)", kind(name))
                        .addStatement("$L().putBytes(0, value, offset, length)", name)
                        .addStatement("limit(offset() + $L + length)", offset(name))
                        .addStatement("return this")
                        .build());

                builder.addMethod(methodBuilder(name)
                        .addModifiers(PUBLIC)
                        .addParameter(DIRECT_BUFFER_TYPE, "value")
                        .returns(thisType)
                        .addStatement("kind($L)", kind(name))
                        .addStatement("$L().putBytes(0, value, 0, value.capacity())", name)
                        .addStatement("limit(offset() + $L + value.capacity())", offset(name))
                        .addStatement("return this")
                        .build());

                builder.addMethod(methodBuilder(name)
                        .addModifiers(PUBLIC)
                        .addParameter(BYTE_ARRAY, "value")
                        .addParameter(int.class, "offset")
                        .addParameter(int.class, "length")
                        .returns(thisType)
                        .addStatement("kind($L)", kind(name))
                        .addStatement("$L().putBytes(0, value, offset, length)", name)
                        .addStatement("limit(offset() + $L + length)", offset(name))
                        .addStatement("return this")
                        .build());

                builder.addMethod(methodBuilder(name)
                        .addModifiers(PUBLIC)
                        .addParameter(BYTE_ARRAY, "value")
                        .returns(thisType)
                        .addStatement("kind($L)", kind(name))
                        .addStatement("$L().putBytes(0, value, 0, value.length)", name)
                        .addStatement("limit(offset() + $L + value.length)", offset(name))
                        .addStatement("return this")
                        .build());

                builder.addMethod(methodBuilder(name)
                        .addModifiers(PUBLIC)
                        .addParameter(String.class, "value")
                        .returns(thisType)
                        .addStatement("kind($L)", kind(name))
                        .addStatement("int length = buffer().putStringWithoutLengthUtf8(offset() + $L, value)", offset(name))
                        .addStatement("limit(offset() + $L + length)", offset(name))
                        .addStatement("return this")
                        .build());
            }

            private void addParameterizedType(
                String name,
                ParameterizedTypeName parameterizedType)
            {
                ClassName consumerType = ClassName.get(Consumer.class);
                ClassName itemType = (ClassName) parameterizedType.typeArguments.get(0);
                ClassName itemBuilderType = itemType.nestedClass("Builder");
                TypeName mutatorType = ParameterizedTypeName.get(consumerType, itemBuilderType);

                builder.addMethod(methodBuilder(name)
                        .addModifiers(PUBLIC)
                        .returns(thisType)
                        .addParameter(mutatorType, "mutator")
                        .addStatement("kind($L)", kind(name))
                        .addStatement("$L().item(mutator)", name)
                        .addStatement("super.limit($LRW.limit())", name)
                        .addStatement("return this")
                        .build());
            }
        }

        private final class WrapMethodGenerator extends MethodSpecGenerator
        {
            private WrapMethodGenerator(
                AstType superType)
            {
                super(methodBuilder("wrap")
                        .addModifiers(PUBLIC)
                        .returns(thisName)
                        .addParameter(MUTABLE_DIRECT_BUFFER_TYPE, "buffer")
                        .addParameter(int.class, "offset")
                        .addParameter(int.class, "maxLimit")
                        .addStatement("super.wrap(buffer, offset, maxLimit)"));
                if (superType != null)
                {
                    builder.addStatement("lastFieldSet = -1");
                }
                builder.addStatement("return this");
            }

            @Override
            public MethodSpec generate()
            {
                return builder.build();
            }

        }

        private final class BuildMethodGenerator extends ClassSpecMixinGenerator
        {
            private String lastParentMemberName;
            private final AstType superType;

            private BuildMethodGenerator(
                ClassName thisType,
                AstType superType,
                TypeSpec.Builder builder)
            {
                super(thisType, builder);
                this.superType = superType;
            }

            public BuildMethodGenerator addParentMember(
                String name)
            {
                lastParentMemberName = name;
                return this;
            }

            @Override
            public TypeSpec.Builder build()
            {
                if (superType != null)
                {
                    builder.addMethod(methodBuilder("build")
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .returns(thisType)
                        .addStatement("assert lastFieldSet == $L : \"Field \\\"$L\\\" is not set\"", index(lastParentMemberName),
                            lastParentMemberName)
                        .addStatement("return super.build()")
                        .build());
                }
                return super.build();
            }
        }

        private static String defaultName(
            String fieldName)
        {
            return String.format("DEFAULT_%s", constant(fieldName));
        }

        private static String dynamicOffset(
            String fieldName)
        {
            return String.format("dynamicOffset%s", initCap(fieldName));
        }

        private void defaultPriorField(
            CodeBlock.Builder code)
        {
            if (priorDefaultValue != null && priorDefaultedIsPrimitive)
            {
                code.addStatement("$L($L)", priorFieldIfDefaulted, defaultName(priorFieldIfDefaulted));
            }
            else
            {
                //  Attempt to default the entire object. This will fail if it has any required fields.
                if (priorDefaultValue == NULL_DEFAULT)
                {
                    if (isVarintType(priorSizeType))
                    {
                        code.addStatement("$L(-1)", methodName(priorSizeName))
                            .addStatement("lastFieldSet = $L", index(priorFieldIfDefaulted));
                    }
                    else if (isVaruintType(priorSizeType))
                    {
                        code.addStatement("$L(0)", methodName(priorSizeName))
                            .addStatement("lastFieldSet = $L", index(priorFieldIfDefaulted));
                    }
                    else
                    {
                        code.addStatement("$L(b -> { })", priorFieldIfDefaulted);
                        code.addStatement("int limit = limit()");
                        code.addStatement("limit($L)", dynamicOffset(priorSizeName));
                        code.addStatement("$L(-1)", methodName(priorSizeName));
                        code.addStatement("limit(limit)");
                    }
                }
                else
                {
                    code.addStatement("$L(b -> { })", priorFieldIfDefaulted);
                }
            }
        }
    }

    private static boolean isStringType(
        ClassName classType)
    {
        String name = classType.simpleName();
        return "StringFW".equals(name) || isString16Type(classType) || isString32Type(classType) || isVarStringType(classType);
    }

    private static boolean isString16Type(
        ClassName classType)
    {
        String name = classType.simpleName();
        return "String16FW".equals(name);
    }

    private static boolean isString32Type(
        ClassName classType)
    {
        String name = classType.simpleName();
        return "String32FW".equals(name);
    }

    private static boolean isVarStringType(
        ClassName classType)
    {
        String name = classType.simpleName();
        return "VarStringFW".equals(name);
    }

    private static boolean isVarintType(
        TypeName type)
    {
        return type instanceof ClassName && "Varint32FW".equals(((ClassName) type).simpleName()) ||
            type instanceof ClassName && "Varint64FW".equals(((ClassName) type).simpleName());
    }

    private static boolean isVaruintType(
        TypeName type)
    {
        return type instanceof ClassName && "Varuint32FW".equals(((ClassName) type).simpleName());
    }

    private static String index(
        String fieldName)
    {
        return String.format("INDEX_%s", constant(fieldName));
    }

    private static String initCap(
        String value)
    {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String kind(
        String fieldName)
    {
        return String.format("KIND_%s", constant(fieldName));
    }

    private static String offset(
        String fieldName)
    {
        return String.format("FIELD_OFFSET_%s", constant(fieldName));
    }

    private static String size(
        String fieldName)
    {
        return String.format("FIELD_SIZE_%s", constant(fieldName));
    }

    private static String fieldName(
        TypeName type)
    {
        String fieldName =  ((ClassName) type).simpleName();
        return String.format("%s%s", Character.toLowerCase(fieldName.charAt(0)), fieldName.substring(1, fieldName.length() - 2));
    }

    private static ClassName enumClassName(
        TypeName enumFWTypeName)
    {
        String enumFWName = ((ClassName) enumFWTypeName).simpleName();
        return ClassName.bestGuess(enumFWName.substring(0, enumFWName.length() - 2));
    }

    private static String enumRO(
        TypeName enumFWTypeName)
    {
        String enumFWName = ((ClassName) enumFWTypeName).simpleName();
        return String.format("%s%sRO", Character.toLowerCase(enumFWName.charAt(0)),
            enumFWName.substring(1, enumFWName.length() - 2));
    }

    private static String enumRW(
        TypeName enumFWTypeName)
    {
        String enumFWName = ((ClassName) enumFWTypeName).simpleName();
        return String.format("%s%sRW", Character.toLowerCase(enumFWName.charAt(0)),
            enumFWName.substring(1, enumFWName.length() - 2));
    }

    private static String constant(
        String fieldName)
    {
        return fieldName.replaceAll("([^_A-Z])([A-Z])", "$1_$2").toUpperCase();
    }

    private static String methodName(String name)
    {
        return RESERVED_METHOD_NAMES.contains(name) ? name + "$" : name;
    }
}
