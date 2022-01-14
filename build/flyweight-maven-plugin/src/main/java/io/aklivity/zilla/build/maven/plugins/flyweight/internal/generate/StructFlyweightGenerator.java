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
package io.aklivity.zilla.build.maven.plugins.flyweight.internal.generate;

import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstAbstractMemberNode.NULL_DEFAULT;
import static io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstByteOrder.NETWORK;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.PrimitiveIterator;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntBinaryOperator;
import java.util.function.IntConsumer;
import java.util.function.IntToLongFunction;
import java.util.function.IntUnaryOperator;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.FieldSpec.Builder;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.WildcardTypeName;

import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstByteOrder;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstEnumNode;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstNamedNode;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstNamedNode.Kind;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstType;

public final class StructFlyweightGenerator extends ClassSpecGenerator
{
    private static final Set<String> RESERVED_METHOD_NAMES = new HashSet<>(Arrays.asList(new String[]
    {
        "offset", "buffer", "limit", "sizeof", "maxLimit", "wrap", "checkLimit", "build", "rewrap"
    }));

    private static final ClassName INT_ITERATOR_CLASS_NAME = ClassName.get(PrimitiveIterator.OfInt.class);

    private static final ClassName LONG_ITERATOR_CLASS_NAME = ClassName.get(PrimitiveIterator.OfLong.class);

    private static final ClassName STRING_CLASS_NAME = ClassName.get(String.class);

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

    private final String baseName;
    private final TypeSpec.Builder builder;
    private final TypeIdGenerator typeId;
    private final MemberFieldGenerator memberField;
    private final MemberSizeConstantGenerator memberSizeConstant;
    private final MemberOffsetConstantGenerator memberOffsetConstant;
    private final MemberAccessorGenerator memberAccessor;
    private final TryWrapMethodGenerator tryWrapMethod;
    private final WrapMethodGenerator wrapMethod;
    private final LimitMethodGenerator limitMethod;
    private final ToStringMethodGenerator toStringMethod;
    private final BuilderClassGenerator builderClass;

    public StructFlyweightGenerator(
        ClassName structName,
        ClassName flyweightName,
        String baseName,
        TypeResolver resolver)
    {
        super(structName);

        this.baseName = baseName;
        this.builder = classBuilder(structName).superclass(flyweightName).addModifiers(PUBLIC, FINAL);
        this.typeId = new TypeIdGenerator(structName, builder);
        this.memberSizeConstant = new MemberSizeConstantGenerator(structName, builder);
        this.memberOffsetConstant = new MemberOffsetConstantGenerator(structName, builder);
        this.memberField = new MemberFieldGenerator(structName, builder);
        this.memberAccessor = new MemberAccessorGenerator(structName, builder);
        this.tryWrapMethod = new TryWrapMethodGenerator(structName);
        this.wrapMethod = new WrapMethodGenerator(structName);
        this.limitMethod = new LimitMethodGenerator();
        this.toStringMethod = new ToStringMethodGenerator();
        this.builderClass = new BuilderClassGenerator(structName, flyweightName, resolver);
    }

    public StructFlyweightGenerator typeId(
        int typeId)
    {
        this.typeId.typeId(typeId);
        return this;
    }

    public StructFlyweightGenerator addMember(
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
        memberOffsetConstant.addMember(name, typeName, unsignedTypeName, size, sizeName);
        memberSizeConstant.addMember(name, type, typeName, unsignedType, unsignedTypeName, size);
        memberField.addMember(name, typeName, unsignedTypeName, size, sizeName, byteOrder, defaultValue);
        memberAccessor.addMember(name, type, typeName, unsignedType, unsignedTypeName, byteOrder, size, sizeName, defaultValue);
        limitMethod.addMember(name, typeName, unsignedTypeName, size, sizeName);
        tryWrapMethod.addMember(name, type, typeName, unsignedType, unsignedTypeName, size, sizeName, defaultValue);
        wrapMethod.addMember(name, type, typeName, unsignedTypeName, size, sizeName, defaultValue);
        toStringMethod.addMember(name, typeName, unsignedTypeName, size, sizeName);
        builderClass.addMember(name, type, typeName, unsignedType, unsignedTypeName, size, sizeName, sizeTypeName,
                usedAsSize, defaultValue, byteOrder);

        return this;
    }

    @Override
    public TypeSpec generate()
    {
        typeId.build();
        memberOffsetConstant.build();
        memberSizeConstant.build();
        memberField.build();
        memberAccessor.build();

        return builder.addMethod(wrapMethod.generate())
                      .addMethod(tryWrapMethod.generate())
                      .addMethod(limitMethod.generate())
                      .addMethod(toStringMethod.generate())
                      .addType(builderClass.generate())
                      .build();
    }

    private static final class TypeIdGenerator extends ClassSpecMixinGenerator
    {
        private int typeId;

        private TypeIdGenerator(
            ClassName thisType,
            TypeSpec.Builder builder)
        {
            super(thisType, builder);
        }

        public void typeId(
            int typeId)
        {
            this.typeId = typeId;
        }

        @Override
        public TypeSpec.Builder build()
        {
            if (typeId != 0)
            {
                builder.addField(FieldSpec.builder(int.class, "TYPE_ID", PUBLIC, STATIC, FINAL)
                        .initializer("$L", String.format("0x%08x", typeId))
                        .build());

                builder.addMethod(methodBuilder("typeId")
                        .addModifiers(PUBLIC)
                        .returns(int.class)
                        .addStatement("return TYPE_ID")
                        .build());
            }
            return builder;
        }
    }

    private static final class MemberSizeConstantGenerator extends ClassSpecMixinGenerator
    {
        private MemberSizeConstantGenerator(
            ClassName thisType,
            TypeSpec.Builder builder)
        {
            super(thisType, builder);
        }

        public MemberSizeConstantGenerator addMember(
            String name,
            AstType type,
            TypeName typeName,
            AstType unsignedType,
            TypeName unsignedTypeName,
            int size)
        {
            if (typeName.isPrimitive())
            {
                builder.addField(
                        FieldSpec.builder(int.class, size(name), PRIVATE, STATIC, FINAL)
                                 .initializer("$L", type.bits() >> 3)
                                 .build());
                if (size != -1)
                {
                    builder.addField(
                            FieldSpec.builder(int.class, arraySize(name), PRIVATE, STATIC, FINAL)
                                .initializer("$L", Integer.toString(size))
                                .build());
                }
            }
            return this;
        }
    }

    private static final class MemberOffsetConstantGenerator extends ClassSpecMixinGenerator
    {
        private String previousName;
        private int previousSize = -1;

        private MemberOffsetConstantGenerator(
            ClassName thisType,
            TypeSpec.Builder builder)
        {
            super(thisType, builder);
        }

        public MemberOffsetConstantGenerator addMember(
            String name,
            TypeName type,
            TypeName unsignedType,
            int size,
            String sizeName)
        {
            String initializer;
            if (previousName == null)
            {
                initializer = "0";
            }
            else if (previousSize == -1)
            {
                initializer = String.format("%s + %s", offset(previousName), size(previousName));
            }
            else
            {
                initializer = String.format("%s + (%s * %s)", offset(previousName), size(previousName),
                        arraySize(previousName));
            }
            builder.addField(
                    FieldSpec.builder(int.class, offset(name), PUBLIC, STATIC, FINAL)
                             .initializer(initializer)
                             .build());

            boolean isFixedSize = type.isPrimitive() && sizeName == null;
            previousName = isFixedSize ? name : null;
            previousSize = size;

            return this;
        }
    }

    private static final class MemberFieldGenerator extends ClassSpecMixinGenerator
    {
        private boolean generateIntPrimitiveIterator;
        private boolean generateLongPrimitiveIterator;

        private MemberFieldGenerator(
            ClassName thisType,
            TypeSpec.Builder builder)
        {
            super(thisType, builder);
        }

        public MemberFieldGenerator addMember(
            String name,
            TypeName type,
            TypeName unsignedType,
            int size,
            String sizeName,
            AstByteOrder byteOrder,
            Object defaultValue)
        {
            if (!type.isPrimitive())
            {
                addNonPrimitiveMember(name, type, unsignedType, byteOrder, defaultValue);
            }
            else if (size != -1 || sizeName != null)
            {
                addIntegerArrayMember(name, type, unsignedType, sizeName != null);
            }
            return this;
        }

        private void addIntegerArrayMember(
            String name,
            TypeName type,
            TypeName unsignedType,
            boolean variableLength)
        {
            if (variableLength)
            {
                builder.addField(TypeName.INT, dynamicLimit(name), PRIVATE);
            }
            ClassName iteratorClass = iteratorClass(thisType, type, unsignedType);
            builder.addField(iteratorClass, iterator(name), PRIVATE);
            TypeName generateType = (unsignedType != null) ? unsignedType : type;
            if (generateType == TypeName.LONG)
            {
                generateLongPrimitiveIterator = true;
            }
            else
            {
                generateIntPrimitiveIterator = true;
            }
        }

        private MemberFieldGenerator addNonPrimitiveMember(
            String name,
            TypeName type,
            TypeName unsignedType,
            AstByteOrder byteOrder,
            Object defaultValue)
        {
            String fieldRO = String.format("%sRO", name);
            Builder fieldBuilder = FieldSpec.builder(type, fieldRO, PRIVATE);
            if (defaultValue == null)
            {
                fieldBuilder.addModifiers(FINAL);
            }

            if (TypeNames.DIRECT_BUFFER_TYPE.equals(type))
            {
                fieldBuilder.initializer("new $T(new byte[0])", UNSAFE_BUFFER_TYPE);
            }
            else if (type instanceof ParameterizedTypeName)
            {
                ParameterizedTypeName parameterizedType = (ParameterizedTypeName) type;
                TypeName typeArgument = parameterizedType.typeArguments.get(0);
                fieldBuilder.initializer("new $T(new $T())", type, typeArgument);
            }
            else if (type instanceof ClassName && (isString16Type((ClassName) type) ||
                    isString32Type((ClassName) type)) && byteOrder == NETWORK)
            {
                fieldBuilder.initializer("new $T($T.BIG_ENDIAN)", type, ByteOrder.class);
            }
            else
            {
                fieldBuilder.initializer("new $T()", type);
            }

            builder.addField(fieldBuilder.build());
            return this;
        }

        @Override
        public TypeSpec.Builder build()
        {
            if (generateIntPrimitiveIterator)
            {
                generateIntPrimitiveIteratorInnerClass();
            }
            if (generateLongPrimitiveIterator)
            {
                generateLongPrimitiveIteratorInnerClass();
            }
            return super.build();
        }

        private void generateIntPrimitiveIteratorInnerClass()
        {
            ClassName intIterator = thisType.nestedClass("IntPrimitiveIterator");
            TypeSpec.Builder builder = classBuilder(intIterator.simpleName())
                    .addModifiers(PRIVATE, FINAL)
                    .addSuperinterface(INT_ITERATOR_CLASS_NAME);
            builder.addField(String.class, "fieldName", PRIVATE, FINAL);
            builder.addField(int.class, "offset", PRIVATE, FINAL);
            builder.addField(int.class, "fieldSize", PRIVATE, FINAL);
            builder.addField(int.class, "count", PRIVATE, FINAL);
            builder.addField(IntUnaryOperator.class, "accessor", PRIVATE, FINAL);
            builder.addField(int.class, "index", PRIVATE);

            builder.addMethod(constructorBuilder()
                    .addParameter(String.class, "fieldName")
                    .addParameter(int.class, "offset")
                    .addParameter(int.class, "fieldSize")
                    .addParameter(int.class, "count")
                    .addParameter(IntUnaryOperator.class, "accessor")
                    .addStatement("this.fieldName = fieldName")
                    .addStatement("this.offset = offset")
                    .addStatement("this.fieldSize = fieldSize")
                    .addStatement("this.count = count")
                    .addStatement("this.accessor = accessor")
                    .build());

            builder.addMethod(MethodSpec.methodBuilder("hasNext")
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .returns(boolean.class)
                    .addStatement("return index < count")
                    .build());

            builder.addMethod(MethodSpec.methodBuilder("nextInt")
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .returns(int.class)
                    .beginControlFlow("if (!hasNext())")
                    .addStatement("throw new $T(fieldName + \": \" + index)", NoSuchElementException.class)
                    .endControlFlow()
                    .addStatement("return accessor.applyAsInt(offset + fieldSize * index++)")
                    .build());

            builder.addMethod(MethodSpec.methodBuilder("toString")
                            .addAnnotation(Override.class)
                            .addModifiers(PUBLIC)
                            .returns(String.class)
                            .addStatement("StringBuffer result = new StringBuffer().append($S)", "[")
                            .addStatement("boolean first = true")
                            .beginControlFlow("while(hasNext())")
                            .beginControlFlow("if (!first)")
                            .addStatement("result.append($S)", ", ")
                            .endControlFlow()
                            .addStatement("result.append(nextInt())")
                            .addStatement("first = false")
                            .endControlFlow()
                            .addStatement("result.append($S)", "]")
                            .addStatement("return result.toString()")
                            .build());

            MemberFieldGenerator.this.builder.addType(builder.build());
        }

        private void generateLongPrimitiveIteratorInnerClass()
        {
            ClassName longIterator = thisType.nestedClass("LongPrimitiveIterator");
            TypeSpec.Builder builder = classBuilder(longIterator.simpleName())
                    .addModifiers(PRIVATE, FINAL)
                    .addSuperinterface(LONG_ITERATOR_CLASS_NAME);
            builder.addField(String.class, "fieldName", PRIVATE, FINAL);
            builder.addField(int.class, "offset", PRIVATE, FINAL);
            builder.addField(int.class, "fieldSize", PRIVATE, FINAL);
            builder.addField(int.class, "count", PRIVATE, FINAL);
            builder.addField(IntToLongFunction.class, "accessor", PRIVATE, FINAL);
            builder.addField(int.class, "index", PRIVATE);

            builder.addMethod(constructorBuilder()
                    .addParameter(String.class, "fieldName")
                    .addParameter(int.class, "offset")
                    .addParameter(int.class, "fieldSize")
                    .addParameter(int.class, "count")
                    .addParameter(IntToLongFunction.class, "accessor")
                    .addStatement("this.fieldName = fieldName")
                    .addStatement("this.offset = offset")
                    .addStatement("this.fieldSize = fieldSize")
                    .addStatement("this.count = count")
                    .addStatement("this.accessor = accessor")
                    .build());

            builder.addMethod(MethodSpec.methodBuilder("hasNext")
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .returns(boolean.class)
                    .addStatement("return index < count")
                    .build());

            builder.addMethod(MethodSpec.methodBuilder("nextLong")
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .returns(long.class)
                    .beginControlFlow("if (!hasNext())")
                    .addStatement("throw new $T(fieldName + \": \" + index)", NoSuchElementException.class)
                    .endControlFlow()
                    .addStatement("return accessor.applyAsLong(offset + fieldSize * index++)")
                    .build());

            builder.addMethod(MethodSpec.methodBuilder("toString")
                            .addAnnotation(Override.class)
                            .addModifiers(PUBLIC)
                            .returns(String.class)
                            .addStatement("StringBuffer result = new StringBuffer().append($S)", "[")
                            .addStatement("boolean first = true")
                            .beginControlFlow("while(hasNext())")
                            .beginControlFlow("if (!first)")
                            .addStatement("result.append($S)", ", ")
                            .endControlFlow()
                            .addStatement("result.append(nextLong())")
                            .addStatement("first = false")
                            .endControlFlow()
                            .addStatement("result.append($S)", "]")
                            .addStatement("return result.toString()")
                            .build());

            MemberFieldGenerator.this.builder.addType(builder.build());
        }
    }

    private static final class MemberAccessorGenerator extends ClassSpecMixinGenerator
    {
        private String anchorLimit;

        private MemberAccessorGenerator(
            ClassName thisType,
            TypeSpec.Builder builder)
        {
            super(thisType, builder);
        }

        public MemberAccessorGenerator addMember(
            String name,
            AstType type,
            TypeName typeName,
            AstType unsignedType,
            TypeName unsignedTypeName,
            AstByteOrder byteOrder,
            int size,
            String sizeName,
            Object defaultValue)
        {
            if (typeName.isPrimitive())
            {
                if (size != -1 || sizeName != null)
                {
                    addIntegerArrayMember(name, typeName, unsignedTypeName, byteOrder, sizeName);
                }
                else
                {
                    addPrimitiveMember(name, type, typeName, unsignedType, unsignedTypeName, byteOrder);
                }
            }
            else
            {
                addNonPrimitiveMember(name, typeName, unsignedTypeName, sizeName, defaultValue);
            }
            return this;
        }

        private void addIntegerArrayMember(
            String name,
            TypeName type,
            TypeName unsignedType,
            AstByteOrder byteOrder,
            String sizeName)
        {
            TypeName generateType = (unsignedType != null) ? unsignedType : type;
            generateType = generateType == TypeName.LONG ? LONG_ITERATOR_CLASS_NAME
                    : INT_ITERATOR_CLASS_NAME;
            builder.addMethod(methodBuilder(methodName(name))
                    .addModifiers(PUBLIC)
                    .returns(generateType)
                    .beginControlFlow("if ($L != null)", iterator(name))
                    .addStatement("$L.index = 0", iterator(name))
                    .endControlFlow()
                    .addStatement("return $L",  iterator(name))
                    .build());
            if (sizeName != null)
            {
                anchorLimit = dynamicLimit(name);
            }
        }

        private void addNonPrimitiveMember(
            String name,
            TypeName type,
            TypeName unsignedType,
            String sizeName,
            Object defaultValue)
        {
            CodeBlock.Builder codeBlock = CodeBlock.builder();

            if (DIRECT_BUFFER_TYPE.equals(type))
            {
                MethodSpec.Builder consumerMethod = methodBuilder(methodName(name))
                        .addModifiers(PUBLIC)
                        .addParameter(IntBinaryOperator.class, "accessor")
                        .returns(type);

                if (anchorLimit != null)
                {
                    consumerMethod.addStatement("accessor.applyAsInt($L + $L, $LRO.capacity())",
                            anchorLimit, offset(name), name);
                }
                else
                {
                    consumerMethod.addStatement("accessor.applyAsInt(offset() + $L, $LRO.capacity())", offset(name), name);
                }

                builder.addMethod(consumerMethod
                        .addStatement("return $LRO", name)
                        .build());
            }

            TypeName returnType = type;
            if (defaultValue == NULL_DEFAULT && sizeName != null)
            {
                codeBlock.addStatement("return $L() == -1 ? null : $LRO", methodName(sizeName), name);
            }
            else if (isVarintType(type))
            {
                codeBlock.addStatement("return $LRO.value()", name);
                returnType = isVarint32Type(type) ? TypeName.INT : TypeName.LONG;
            }
            else if (isVaruint32Type(type))
            {
                codeBlock.addStatement("return $LRO.value()", name);
                returnType = TypeName.INT;
            }
            else
            {
                codeBlock.addStatement("return $LRO", name);
            }

            anchorLimit = name + "RO." + (DIRECT_BUFFER_TYPE.equals(type) ? "capacity()" : "limit()");

            builder.addMethod(methodBuilder(methodName(name))
                    .addModifiers(PUBLIC)
                    .returns(returnType)
                    .addCode(codeBlock.build())
                    .build());
        }

        private void addPrimitiveMember(
            String name,
            AstType type,
            TypeName typeName,
            AstType unsignedType,
            TypeName unsignedTypeName,
            AstByteOrder byteOrder)
        {
            TypeName generateType = (unsignedTypeName != null) ? unsignedTypeName : typeName;

            CodeBlock.Builder codeBlock = CodeBlock.builder();

            String getterName = GETTER_NAMES.get(typeName);
            if (getterName == null)
            {
                throw new IllegalStateException("member type not supported: " + typeName);
            }

            if (type.bits() == 24)
            {
                if (anchorLimit != null)
                {
                    codeBlock.addStatement("int offset = $L + $L", anchorLimit, offset(name));
                }
                else
                {
                    codeBlock.addStatement("int offset = offset() + $L", offset(name));
                }

                codeBlock.add("$[")
                         .add("int bits = (buffer().getByte(offset) & 0xff) << 16 |")
                         .add(" (buffer().getByte(offset + 1) & 0xff) << 8 |")
                         .add(" (buffer().getByte(offset + 2) & 0xff)")
                         .add(";\n$]");

                if (byteOrder != AstByteOrder.NETWORK)
                {
                    codeBlock.beginControlFlow("if ($T.NATIVE_BYTE_ORDER != $T.BIG_ENDIAN)", BUFFER_UTIL_TYPE, ByteOrder.class);
                    codeBlock.addStatement("bits = Integer.reverseBytes(bits) >> 8");
                    codeBlock.endControlFlow();
                }

                codeBlock.addStatement("return bits");
            }
            else
            {
                codeBlock.add("$[").add("return ");

                if (generateType != typeName)
                {
                    codeBlock.add("($T)(", generateType);
                }

                if (anchorLimit != null)
                {
                    codeBlock.add("buffer().$L($L + $L", getterName, anchorLimit, offset(name));
                }
                else
                {
                    codeBlock.add("buffer().$L(offset() + $L", getterName, offset(name));
                }

                if (byteOrder == AstByteOrder.NETWORK)
                {
                    if (typeName == TypeName.SHORT || typeName == TypeName.INT || typeName == TypeName.LONG)
                    {
                        codeBlock.add(", $T.BIG_ENDIAN", ByteOrder.class);
                    }
                }

                if (generateType != typeName)
                {
                    if (typeName == TypeName.BYTE)
                    {
                        codeBlock.add(") & 0xFF)");
                    }
                    else if (typeName == TypeName.SHORT)
                    {
                        codeBlock.add(") & 0xFFFF)");
                    }
                    else if (typeName == TypeName.INT)
                    {
                        codeBlock.add(") & 0xFFFF_FFFFL)");
                    }
                    else if (typeName == TypeName.LONG)
                    {
                        codeBlock.add(") & 0xFFFF_FFFF)");
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

            builder.addMethod(methodBuilder(methodName(name))
                    .addModifiers(PUBLIC)
                    .returns(generateType)
                    .addCode(codeBlock.build())
                    .build());
        }
    }

    private final class LimitMethodGenerator extends MethodSpecGenerator
    {
        private String anchorName;
        private TypeName anchorType;
        private String lastName;
        private TypeName lastType;
        private int lastSize;
        private String lastSizeName;

        private LimitMethodGenerator()
        {
            super(methodBuilder("limit")
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .returns(int.class));
        }

        public LimitMethodGenerator addMember(
            String name,
            TypeName type,
            TypeName unsignedType,
            int size,
            String sizeName)
        {
            if (!type.isPrimitive() || type.isPrimitive() && sizeName != null)
            {
                anchorName = name;
                anchorType = type;
            }

            lastName = name;
            lastType = type;
            lastSize = size;
            lastSizeName = sizeName;

            return this;
        }

        @Override
        public MethodSpec generate()
        {
            if (lastName == null)
            {
                builder.addStatement("return offset()");
            }
            else
            {
                CodeBlock.Builder code = CodeBlock.builder();
                code.add("$[");
                if (anchorName != null)
                {
                    if (TypeNames.DIRECT_BUFFER_TYPE.equals(anchorType))
                    {
                        code.add("return $L().capacity()", methodName(anchorName));
                    }
                    else if (anchorType.isPrimitive()) // variable length array
                    {
                        code.add("return $L", dynamicLimit(anchorName));
                    }
                    else
                    {
                        code.add("return $LRO.limit()", anchorName);
                    }
                }
                else
                {
                    code.add("return offset()");
                }
                if (lastType.isPrimitive())
                {
                    if (lastSize != -1) // fixed size array
                    {
                        code.add(" + $L + ($L * $L)", offset(lastName), size(lastName), arraySize(lastName));
                    }
                    else if (lastSizeName == null) // not an array
                    {
                        code.add(" + $L + $L", offset(lastName), size(lastName));
                    }
                }
                code.add(";\n$]");
                builder.addCode(code.build());
            }

            return builder.build();
        }

    }
    private final class TryWrapMethodGenerator extends MethodSpecGenerator
    {
        private final ClassName thisType;
        private String anchorLimit;
        private boolean limitDeclared;

        private TryWrapMethodGenerator(
            ClassName thisType)
        {
            super(methodBuilder("tryWrap")
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .addParameter(DIRECT_BUFFER_TYPE, "buffer")
                    .addParameter(int.class, "offset")
                    .addParameter(int.class, "maxLimit")
                    .returns(thisName));
            addFailIfStatement("null == super.tryWrap(buffer, offset, maxLimit)");
            this.thisType = thisType;
        }

        private void addFailIfStatement(
            String string,
            Object... args)
        {
            builder.beginControlFlow("if (" + string + ")", args);
            builder.addStatement("return null");
            builder.endControlFlow();
        }

        private void declareLimit()
        {
            if (!limitDeclared)
            {
                builder.addStatement("int limit");
                limitDeclared = true;
            }
        }

        public TryWrapMethodGenerator addMember(
            String name,
            AstType type,
            TypeName typeName,
            AstType unsignedType,
            TypeName unsignedTypeName,
            int size,
            String sizeName,
            Object defaultValue)
        {
            if (DIRECT_BUFFER_TYPE.equals(typeName))
            {
                // TODO: is this dead code? I can't find a case where type should be equal to DirectBuffer
                // and we never get here during generation of the test idl during build
                addFailIfStatement("null == $LRO.wrap(buffer, offset + $L, maxLimit - (offset + $L))",
                        name, offset(name), offset(name));
            }
            else if (!typeName.isPrimitive())
            {
                addNonPrimitiveMember(name, typeName, unsignedTypeName, size, sizeName, defaultValue);
            }
            else if (size != -1)
            {
                addFixedIntegerArrayMember(name, type, typeName, unsignedType, unsignedTypeName, size);
            }
            else if (sizeName != null)
            {
                addVariableIntegerArrayMember(name, type, typeName, unsignedType, unsignedTypeName, sizeName);
            }
            return this;
        }

        private void addFixedIntegerArrayMember(
            String name,
            AstType type,
            TypeName typeName,
            AstType unsignedType,
            TypeName unsignedTypeName,
            int size)
        {
            ClassName iteratorClass = iteratorClass(thisType, typeName, unsignedTypeName);
            TypeName targetTypeName = (unsignedTypeName != null) ? unsignedTypeName : typeName;
            targetTypeName = targetTypeName == TypeName.LONG ? targetTypeName : TypeName.INT;
            CodeBlock.Builder code = CodeBlock.builder();
            String offsetName;
            if (anchorLimit != null)
            {
                offsetName = "offset" + initCap(name);
                code.addStatement("final int $L = $L + $L", offsetName, anchorLimit, offset(name));
            }
            else
            {
                offsetName = "offset + " + offset(name);
            }
            code.add("$[")
                .add("$L = new $T($S, $L, $L, $L, o -> ",
                        iterator(name), iteratorClass, name, offsetName, size(name), arraySize(name));
            addBufferGet(code, targetTypeName, type, typeName, unsignedTypeName, "o");
            code.add(")")
                .add(";\n$]");

            builder.addCode(code.build());
        }

        private void addVariableIntegerArrayMember(
            String name,
            AstType type,
            TypeName typeName,
            AstType unsignedType,
            TypeName unsignedTypeName,
            String sizeName)
        {
            ClassName iteratorClass = iteratorClass(thisType, typeName, unsignedTypeName);
            String offsetName = "offset" + initCap(name);
            String limitName = "limit" + initCap(name);
            TypeName targetType = (unsignedTypeName != null) ? unsignedTypeName : typeName;
            targetType = targetType == TypeName.LONG ? targetType : TypeName.INT;
            CodeBlock.Builder code = CodeBlock.builder();
            if (anchorLimit != null)
            {
                code.addStatement("final int $L = $L + $L", offsetName, anchorLimit, offset(name));
            }
            else
            {
                code.addStatement("final int $L = offset + $L", offsetName, offset(name));
            }
            code.add("$[")
                .add("$L = $L() == -1 ? null :\n", iterator(name), methodName(sizeName))
                .add("new $T($S, $L, $L, (int) $L(), o -> ",
                    iteratorClass, name, offsetName, size(name), methodName(sizeName));
            addBufferGet(code, targetType, type, typeName, unsignedTypeName, "o");
            code.add(")")
                .add(";\n$]")
                .addStatement("$L = $L() == -1 ? $L : $L + $L * $L()", limitName, methodName(sizeName),
                        offsetName, offsetName, size(name), methodName(sizeName));
            builder.addCode(code.build());
            anchorLimit = limitName;
        }

        private void addNonPrimitiveMember(
            String name,
            TypeName type,
            TypeName unsignedType,
            int size,
            String sizeName,
            Object defaultValue)
        {
            boolean sized = size >= 0 || sizeName != null;
            if (sized)
            {
                declareLimit();
            }
            if (anchorLimit != null)
            {
                if (size >= 0)
                {
                    builder.addStatement("limit = $L + $L + $L", anchorLimit, offset(name), size);
                }
                else if (sizeName != null)
                {
                    if (defaultValue == NULL_DEFAULT)
                    {
                        builder.addStatement("limit = $L + $L + ((int) $L() == -1 ? 0 : (int) $L()) ",
                                anchorLimit, offset(name), methodName(sizeName), methodName(sizeName));
                    }
                    else
                    {
                        builder.addStatement("limit = $L + $L + (int) $L()", anchorLimit,
                                offset(name), methodName(sizeName));
                    }
                }
                if (sized)
                {
                    addFailIfStatement("limit > maxLimit || null == $LRO.tryWrap(buffer, $L + $L, limit)",
                                       name, anchorLimit, offset(name));
                }
                else
                {
                    addFailIfStatement("null == $LRO.tryWrap(buffer, $L + $L, maxLimit)",
                            name, anchorLimit, offset(name));
                }
            }
            else
            {
                if (size >= 0)
                {
                    builder.addStatement("limit = offset + $L + $L",
                            offset(name), size);
                }
                else if (sizeName != null)
                {
                    if (defaultValue == NULL_DEFAULT)
                    {
                        builder.addStatement("limit = offset + $L + ((int) $L() == -1 ? 0 : (int) $L())",
                                offset(name), methodName(sizeName), methodName(sizeName));
                    }
                    else
                    {
                        builder.addStatement("limit = offset + $L + (int) $L()",
                                offset(name), methodName(sizeName));
                    }
                }
                if (sized)
                {
                    addFailIfStatement("limit > maxLimit || null == $LRO.tryWrap(buffer, offset + $L, limit)",
                                       name, offset(name));
                }
                else
                {
                    addFailIfStatement("null == $LRO.tryWrap(buffer, offset + $L, maxLimit)",
                            name, offset(name));
                }
            }
            anchorLimit = name + "RO.limit()";
        }

        private void addBufferGet(
            CodeBlock.Builder codeBlock,
            TypeName targetTypeName,
            AstType type,
            TypeName typeName,
            TypeName unsignedTypeName,
            String offset)
        {
            if (type.bits() == 24)
            {
                if (type.isUnsignedInt())
                {
                    codeBlock.add("$T.NATIVE_BYTE_ORDER == $T.BIG_ENDIAN ? ", BUFFER_UTIL_TYPE, ByteOrder.class)
                             .add("(buffer().getByte($L) & 0xff) << 16 |", offset)
                             .add(" (buffer().getByte($L + 1) & 0xff) << 8 |", offset)
                             .add(" (buffer().getByte($L + 2) & 0xff)", offset)
                             .add(" : ")
                             .add("(buffer().getByte($L + 2) & 0xff) << 16 |", offset)
                             .add(" (buffer().getByte($L + 1) & 0xff) << 8 |", offset)
                             .add(" (buffer().getByte($L) & 0xff)", offset);
                }
                else
                {
                    codeBlock.add("$T.NATIVE_BYTE_ORDER == $T.BIG_ENDIAN ? ", BUFFER_UTIL_TYPE, ByteOrder.class)
                             .add("buffer().getByte($L) << 16 |", offset)
                             .add(" (buffer().getByte($L + 1) & 0xff) << 8 |", offset)
                             .add(" (buffer().getByte($L + 2) & 0xff)", offset)
                             .add(" : ")
                             .add("buffer().getByte($L + 2) << 16 |", offset)
                             .add(" (buffer().getByte($L + 1) & 0xff) << 8 |", offset)
                             .add(" (buffer().getByte($L) & 0xff)", offset);
                }
            }
            else
            {
                String getterName = GETTER_NAMES.get(typeName);
                if (getterName == null)
                {
                    throw new IllegalStateException("member type not supported: " + typeName);
                }
                if (targetTypeName != typeName)
                {
                    codeBlock.add("($T)(", targetTypeName);
                }

                codeBlock.add("buffer().$L($L", getterName, offset);

                if (targetTypeName != typeName  && unsignedTypeName != null)
                {
                    if (typeName == TypeName.BYTE)
                    {
                        codeBlock.add(") & 0xFF)");
                    }
                    else if (typeName == TypeName.SHORT)
                    {
                        codeBlock.add(") & 0xFFFF)", ByteOrder.class);
                    }
                    else if (typeName == TypeName.INT)
                    {
                        codeBlock.add(") & 0xFFFF_FFFFL)", ByteOrder.class);
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
                if (targetTypeName != typeName && unsignedTypeName == null)
                {
                    codeBlock.add(")");
                }
            }
        }

        @Override
        public MethodSpec generate()
        {
            addFailIfStatement("limit() > maxLimit");
            return builder.addStatement("return this")
                          .build();
        }
    }

    private final class WrapMethodGenerator extends MethodSpecGenerator
    {
        private final ClassName thisType;
        private String anchorLimit;

        private WrapMethodGenerator(
            ClassName thisType)
        {
            super(methodBuilder("wrap")
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .addParameter(DIRECT_BUFFER_TYPE, "buffer")
                    .addParameter(int.class, "offset")
                    .addParameter(int.class, "maxLimit")
                    .returns(thisName));
            builder.addStatement("super.wrap(buffer, offset, maxLimit)");
            this.thisType = thisType;
        }

        public WrapMethodGenerator addMember(
            String name,
            AstType type,
            TypeName typeName,
            TypeName unsignedTypeName,
            int size,
            String sizeName,
            Object defaultValue)
        {
            if (DIRECT_BUFFER_TYPE.equals(typeName))
            {
                // TODO: is this dead code? I can't find a case where type should be equal to DirectBuffer
                // and we never get here during generation of the test idl during build
                builder.addStatement("$LRO.wrap(buffer, offset + $L, maxLimit - (offset + $L))",
                        name, offset(name), offset(name));
            }
            else if (!typeName.isPrimitive())
            {
                addNonPrimitiveMember(name, typeName, unsignedTypeName, size, sizeName, defaultValue);
            }
            else if (size != -1)
            {
                addFixedIntegerArrayMember(name, type, typeName, unsignedTypeName, size);
            }
            else if (sizeName != null)
            {
                addVariableIntegerArrayMember(name, type, typeName, unsignedTypeName, sizeName);
            }
            return this;
        }

        private void addFixedIntegerArrayMember(
            String name,
            AstType type,
            TypeName typeName,
            TypeName unsignedTypeName,
            int size)
        {
            ClassName iteratorClass = iteratorClass(thisType, typeName, unsignedTypeName);
            TypeName targetType = (unsignedTypeName != null) ? unsignedTypeName : typeName;
            targetType = targetType == TypeName.LONG ? targetType : TypeName.INT;
            CodeBlock.Builder code = CodeBlock.builder();
            String offsetName;
            if (anchorLimit != null)
            {
                offsetName = "offset" + initCap(name);
                code.addStatement("final int $L = $L + $L", offsetName, anchorLimit, offset(name));
            }
            else
            {
                offsetName = "offset + " + offset(name);
            }
            code.add("$[")
                .add("$L = new $T($S, $L, $L, $L, o -> ",
                        iterator(name), iteratorClass, name, offsetName, size(name), arraySize(name));
            addBufferGet(code, targetType, type, typeName, unsignedTypeName, "o");
            code.add(")")
                .add(";\n$]");
            builder.addCode(code.build());
        }

        private void addVariableIntegerArrayMember(
            String name,
            AstType type,
            TypeName typeName,
            TypeName unsignedTypeName,
            String sizeName)
        {
            ClassName iteratorClass = iteratorClass(thisType, typeName, unsignedTypeName);
            String offsetName = "offset" + initCap(name);
            String limitName = "limit" + initCap(name);
            TypeName targetType = (unsignedTypeName != null) ? unsignedTypeName : typeName;
            targetType = targetType == TypeName.LONG ? targetType : TypeName.INT;
            CodeBlock.Builder code = CodeBlock.builder();
            if (anchorLimit != null)
            {
                code.addStatement("final int $L = $L + $L", offsetName, anchorLimit, offset(name));
            }
            else
            {
                code.addStatement("final int $L = offset + $L", offsetName, offset(name));
            }
            code.add("$[")
                .add("$L = $L() == -1 ? null :\n", iterator(name), methodName(sizeName))
                .add("new $T($S, $L, $L, (int) $L(), o -> ",
                    iteratorClass, name, offsetName, size(name), methodName(sizeName));
            addBufferGet(code, targetType, type, typeName, unsignedTypeName, "o");
            code.add(")")
                .add(";\n$]")
                .addStatement("$L = $L() == -1 ? $L : $L + $L * $L()", limitName, methodName(sizeName),
                        offsetName, offsetName, size(name), methodName(sizeName));
            builder.addCode(code.build());
            anchorLimit = limitName;
        }

        private void addNonPrimitiveMember(
            String name,
            TypeName type,
            TypeName unsignedType,
            int size,
            String sizeName,
            Object defaultValue)
        {
            if (anchorLimit != null)
            {
                if (size >= 0)
                {
                    builder.addStatement("$LRO.wrap(buffer, $L + $L, $L + $L + $L)",
                            name, anchorLimit, offset(name), anchorLimit, offset(name), size);
                }
                else if (sizeName != null)
                {
                    if (defaultValue == NULL_DEFAULT)
                    {
                        builder.addStatement(
                            "$LRO.wrap(buffer, $L + $L, $L + $L + ((int) $L() == -1 ? 0 : (int) $L()))",
                            name, anchorLimit, offset(name), anchorLimit, offset(name), methodName(sizeName),
                            methodName(sizeName));
                    }
                    else
                    {
                        builder.addStatement("$LRO.wrap(buffer, $L + $L, $L + $L + (int) $L())",
                            name, anchorLimit, offset(name), anchorLimit, offset(name), methodName(sizeName));
                    }
                }
                else
                {
                    builder.addStatement("$LRO.wrap(buffer, $L + $L, maxLimit)",
                            name, anchorLimit, offset(name));
                }
            }
            else
            {
                if (size >= 0)
                {
                    builder.addStatement("$LRO.wrap(buffer, offset + $L, offset + $L + $L)",
                            name, offset(name), offset(name), size);
                }
                else if (sizeName != null)
                {
                    if (defaultValue == NULL_DEFAULT)
                    {
                        builder.addStatement(
                                "$LRO.wrap(buffer, offset + $L, offset + $L + ((int) $L() == -1 ? 0 : (int) $L()))",
                                name, offset(name), offset(name), methodName(sizeName), methodName(sizeName));
                    }
                    else
                    {
                        builder.addStatement(
                                "$LRO.wrap(buffer, offset + $L, offset + $L + (int) $L())",
                                name, offset(name), offset(name), methodName(sizeName));
                    }
                }
                else
                {
                    builder.addStatement("$LRO.wrap(buffer, offset + $L, maxLimit)",
                            name, offset(name));
                }
            }
            anchorLimit = name + "RO.limit()";
        }

        private void addBufferGet(
            CodeBlock.Builder codeBlock,
            TypeName targetType,
            AstType type,
            TypeName typeName,
            TypeName unsignedTypeName,
            String offset)
        {
            if (type.bits() == 24)
            {
                if (type.isUnsignedInt())
                {
                    codeBlock.add("$T.NATIVE_BYTE_ORDER == $T.BIG_ENDIAN ? ", BUFFER_UTIL_TYPE, ByteOrder.class)
                             .add("(buffer().getByte($L) & 0xff) << 16 |", offset)
                             .add(" (buffer().getByte($L + 1) & 0xff) << 8 |", offset)
                             .add(" (buffer().getByte($L + 2) & 0xff)", offset)
                             .add(" : ")
                             .add("(buffer().getByte($L + 2) & 0xff) << 16 |", offset)
                             .add(" (buffer().getByte($L + 1) & 0xff) << 8 |", offset)
                             .add(" (buffer().getByte($L) & 0xff)", offset);
                }
                else
                {
                    codeBlock.add("$T.NATIVE_BYTE_ORDER == $T.BIG_ENDIAN ? ", BUFFER_UTIL_TYPE, ByteOrder.class)
                             .add("buffer().getByte($L) << 16 |", offset)
                             .add(" (buffer().getByte($L + 1) & 0xff) << 8 |", offset)
                             .add(" (buffer().getByte($L + 2) & 0xff)", offset)
                             .add(" : ")
                             .add("buffer().getByte($L + 2) << 16 |", offset)
                             .add(" (buffer().getByte($L + 1) & 0xff) << 8 |", offset)
                             .add(" (buffer().getByte($L) & 0xff)", offset);
                }
            }
            else
            {
                String getterName = GETTER_NAMES.get(typeName);
                if (getterName == null)
                {
                    throw new IllegalStateException("member type not supported: " + typeName);
                }
                if (targetType != typeName)
                {
                    codeBlock.add("($T)(", targetType);
                }

                codeBlock.add("buffer().$L($L", getterName, offset);

                if (targetType != typeName  && unsignedTypeName != null)
                {
                    if (typeName == TypeName.BYTE)
                    {
                        codeBlock.add(") & 0xFF)");
                    }
                    else if (typeName == TypeName.SHORT)
                    {
                        codeBlock.add(") & 0xFFFF)", ByteOrder.class);
                    }
                    else if (typeName == TypeName.INT)
                    {
                        codeBlock.add(") & 0xFFFF_FFFFL)", ByteOrder.class);
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
                if (targetType != typeName && unsignedTypeName == null)
                {
                    codeBlock.add(")");
                }
            }
        }

        @Override
        public MethodSpec generate()
        {
            return builder.addStatement("checkLimit(limit(), maxLimit)")
                          .addStatement("return this")
                          .build();
        }
    }

    private final class ToStringMethodGenerator extends MethodSpecGenerator
    {
        private final List<String> formats = new LinkedList<>();
        private final List<String> args = new LinkedList<>();

        private ToStringMethodGenerator()
        {
            super(methodBuilder("toString")
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .returns(String.class));
        }

        public ToStringMethodGenerator addMember(
            String name,
            TypeName type,
            TypeName unsignedType,
            int size,
            String sizeName)
        {
            boolean isArray = size != -1 || sizeName != null;
            formats.add(String.format("%s=%%%s", name, type.isPrimitive() && !isArray ? "d" : "s"));
            if (type instanceof ClassName && isStringType((ClassName) type))
            {
                args.add(String.format("%sRO.asString()", name));
            }
            else
            {
                args.add(String.format("%s()", name));
            }
            return this;
        }

        @Override
        public MethodSpec generate()
        {
            String typeName = constant(baseName);
            if (formats.isEmpty())
            {
                builder.addStatement("return $S", typeName);
            }
            else
            {
                String format = String.format("%s [%s]", typeName, String.join(", ", formats));
                builder.addStatement("return String.format($S, $L)", format, String.join(", ", args));
            }
            return builder.build();
        }

    }

    private static final class BuilderClassGenerator extends ClassSpecGenerator
    {
        private final TypeSpec.Builder builder;
        private final ClassName structType;
        private final MemberConstantGenerator memberConstant;
        private final MemberFieldGenerator memberField;
        private final MemberAccessorGenerator memberAccessor;
        private final MemberMutatorGenerator memberMutator;
        private final WrapMethodGenerator wrapMethod;
        private final WrapMethodWithArrayGenerator wrapMethodWithArray;
        private final TypeResolver resolver;
        private String priorFieldIfDefaulted;
        private boolean priorDefaultedIsPrimitive;
        private boolean priorDefaultedIsEnum;
        private boolean priorDefaultedIsString;
        private Object priorDefaultValue;
        private String priorSizeName;
        private TypeName priorSizeType;

        private BuilderClassGenerator(
            ClassName structType,
            ClassName flyweightType,
            TypeResolver resolver)
        {
            this(structType.nestedClass("Builder"), flyweightType.nestedClass("Builder"), structType, resolver);
        }

        private BuilderClassGenerator(
            ClassName thisType,
            ClassName builderRawType,
            ClassName structType,
            TypeResolver resolver)
        {
            super(thisType);
            this.builder = classBuilder(thisType.simpleName())
                    .addModifiers(PUBLIC, STATIC, FINAL)
                    .superclass(ParameterizedTypeName.get(builderRawType, structType));
            this.structType = structType;
            this.memberConstant = new MemberConstantGenerator(thisType, resolver, builder);
            this.memberField = new MemberFieldGenerator(thisType, builder);
            this.memberAccessor = new MemberAccessorGenerator(thisType, builder);
            this.memberMutator = new MemberMutatorGenerator(thisType, resolver, builder);
            this.wrapMethod = new WrapMethodGenerator(thisType, builder);
            this.wrapMethodWithArray = new WrapMethodWithArrayGenerator(structType, resolver);
            this.resolver = resolver;
        }

        private void addMember(
            String name,
            AstType type,
            TypeName typeName,
            AstType unsignedType,
            TypeName unsignedTypeName,
            int size,
            String sizeName,
            TypeName sizeType,
            boolean usedAsSize,
            Object defaultValue,
            AstByteOrder byteOrder)
        {
            if (usedAsSize)
            {
                defaultValue = 0;
            }
            Consumer<CodeBlock.Builder> defaultPriorField = priorFieldIfDefaulted == null ? null
                    : b -> defaultPriorField(b);
            memberConstant.addMember(name, type, typeName, unsignedTypeName, size, sizeName, usedAsSize, defaultValue);
            memberField.addMember(name, typeName, unsignedTypeName, size, sizeName, usedAsSize, byteOrder);
            memberAccessor.addMember(name, typeName, unsignedTypeName, usedAsSize, size, sizeName, defaultValue,
                    priorFieldIfDefaulted, defaultPriorField);
            memberMutator.addMember(name, type, typeName, unsignedType, unsignedTypeName, usedAsSize, size, sizeName, sizeType,
                    byteOrder, defaultValue, priorFieldIfDefaulted, defaultPriorField);
            wrapMethod.addMember(name, typeName, unsignedTypeName, usedAsSize, size, sizeName, sizeType,
                    byteOrder, defaultValue, priorFieldIfDefaulted, defaultPriorField);
            wrapMethodWithArray.addMember(name, typeName, usedAsSize, size, sizeName);
            if (defaultValue != null || isImplicitlyDefaulted(typeName, size, sizeName, type, resolver))
            {
                priorFieldIfDefaulted = name;
                priorDefaultedIsPrimitive =
                        typeName.isPrimitive() || isVarintType(typeName) ||
                        isVaruintType(typeName) || isVaruintnType(typeName);
                priorDefaultedIsString = isStringType(typeName);
                AstNamedNode node = type != null ? resolver.resolve(type.name()) : null;
                priorDefaultedIsEnum = node != null && isEnumType(node.getKind());
                priorDefaultValue = defaultValue;
                priorSizeName = sizeName;
                priorSizeType = sizeType;
            }
            else
            {
                priorFieldIfDefaulted = null;
            }
        }

        @Override
        public TypeSpec generate()
        {
            memberConstant.build();
            memberField.build();
            memberAccessor.build();
            memberMutator.build();
            return builder.addMethod(constructor())
                          .addMethod(wrapMethod.generate())
                          .addMethod(wrapMethodWithArray.generate())
                          .addMethod(rewrapMethod())
                          .addMethod(buildMethod())
                          .build();
        }

        private MethodSpec constructor()
        {
            return constructorBuilder()
                    .addModifiers(PUBLIC)
                    .addStatement("super(new $T())", structType)
                    .build();
        }

        private MethodSpec buildMethod()
        {
            MethodSpec.Builder builder = methodBuilder("build")
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .returns(structType);
            if (priorFieldIfDefaulted != null)
            {
                builder.beginControlFlow("if (lastFieldSet < $L)", index(priorFieldIfDefaulted));
                CodeBlock.Builder code = CodeBlock.builder();
                defaultPriorField(code);
                builder.addCode(code.build());
                builder.endControlFlow();
            }
            return builder.addStatement("assert lastFieldSet == FIELD_COUNT - 1")
                          .addStatement("lastFieldSet = -1")
                          .addStatement("return super.build()")
                          .build();
        }

        private MethodSpec rewrapMethod()
        {
            return methodBuilder("rewrap")
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .returns(thisName)
                    .addStatement("super.rewrap()")
                    .addStatement("return this")
                    .build();
        }

        private static String appendMethodName(
            String fieldName)
        {
            return String.format("append%s", initCap(fieldName));
        }

        private static String defaultName(
            String fieldName)
        {
            return String.format("DEFAULT_%s", constant(fieldName));
        }

        private static boolean isImplicitlyDefaulted(
            TypeName typeName,
            int size,
            String sizeName,
            AstType type,
            TypeResolver resolver)
        {
            boolean result = false;
            AstNamedNode node = type != null ? resolver.resolve(type.name()) : null;
            final boolean isEnumType = node != null && isEnumType(node.getKind());
            if (typeName instanceof ClassName && !isStringType((ClassName) typeName) && !isVarintType(typeName) &&
                !isVaruintType(typeName) && !isVaruintnType(typeName) && !isEnumType)
            {
                ClassName classType = (ClassName) typeName;
                if ("OctetsFW".equals(classType.simpleName()))
                {
                    result = size == -1 && sizeName == null;
                }
                else
                {
                    result = true;
                }
            }
            if (typeName instanceof ParameterizedTypeName)
            {
                ParameterizedTypeName parameterizedType = (ParameterizedTypeName) typeName;
                if ("ListFW".equals(parameterizedType.rawType.simpleName()) ||
                    "Array32FW".equals(parameterizedType.rawType.simpleName()))
                {
                    result = true;
                }
            }
            return result;
        }

        private static String dynamicOffset(
            String fieldName)
        {
            return String.format("dynamicOffset%s", initCap(fieldName));
        }

        private static String dynamicValue(
            String fieldName)
        {
            return String.format("dynamicValue%s", initCap(fieldName));
        }

        // TODO: Varuint32 should NEVER be < 0
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
                    else if (isVaruintType(priorSizeType) || isVaruintnType(priorSizeType))
                    {
                        code.addStatement("$L(0)", methodName(priorSizeName))
                            .addStatement("lastFieldSet = $L", index(priorFieldIfDefaulted));
                    }
                    else if (priorDefaultedIsString)
                    {
                        code.addStatement("$L((String) null)", priorFieldIfDefaulted);
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
                else if (priorDefaultedIsEnum)
                {
                    code.addStatement("$L(b -> b.set($L))", priorFieldIfDefaulted, defaultName(priorFieldIfDefaulted));
                }
                else if (priorDefaultedIsString)
                {
                    code.addStatement("$L($L)", priorFieldIfDefaulted, priorDefaultValue);
                }
                else
                {
                    code.addStatement("$L(b -> { })", priorFieldIfDefaulted);
                }
            }
        }

        private static final class MemberConstantGenerator extends ClassSpecMixinGenerator
        {
            private final TypeResolver resolver;
            private int nextIndex;

            private MemberConstantGenerator(
                ClassName thisType,
                TypeResolver resolver,
                TypeSpec.Builder builder)
            {
                super(thisType, builder);
                this.resolver = resolver;
            }

            public MemberConstantGenerator addMember(
                String name,
                AstType type,
                TypeName typeName,
                TypeName unsignedType,
                int size,
                String sizeName,
                boolean usedAsSize,
                Object defaultValue)
            {
                boolean automaticallySet = usedAsSize && !isVarintType(typeName) &&
                        !isVaruintType(typeName) && !isVaruintnType(typeName);
                if (!automaticallySet)
                {
                    builder.addField(
                            FieldSpec.builder(int.class, index(name), PRIVATE, STATIC, FINAL)
                                     .initializer(Integer.toString(nextIndex++))
                                     .build());
                }
                boolean isOctetsType = isOctetsType(typeName);
                if (defaultValue != null && !isOctetsType)
                {
                    Object defaultValueToSet = defaultValue == NULL_DEFAULT ? null : defaultValue;
                    TypeName generateType = (unsignedType != null) ? unsignedType : typeName;
                    if (size != -1 || sizeName != null)
                    {
                        generateType = generateType == TypeName.LONG ? LONG_ITERATOR_CLASS_NAME
                                : INT_ITERATOR_CLASS_NAME;
                    }
                    if (isVaruint32Type(typeName))
                    {
                        generateType = TypeName.INT;
                    }
                    else if (isVarint32Type(typeName))
                    {
                        generateType = TypeName.INT;
                    }
                    else if (isVarint64Type(typeName))
                    {
                        generateType = TypeName.LONG;
                    }
                    else if (isStringType(typeName))
                    {
                        generateType = STRING_CLASS_NAME;
                    }
                    AstNamedNode node = resolver.resolve(type.name());
                    if (node != null && isEnumType(node.getKind()))
                    {
                        AstEnumNode enumNode = (AstEnumNode) node;
                        ClassName enumFlyweightName = (ClassName) typeName;
                        ClassName enumName = enumFlyweightName.peerClass(enumNode.name());
                        builder.addField(
                            FieldSpec.builder(enumName, defaultName(name), PUBLIC, STATIC, FINAL)
                                .initializer("$T.$L", enumName, Objects.toString(defaultValueToSet))
                                .build());
                    }
                    else
                    {
                        builder.addField(
                            FieldSpec.builder(generateType, defaultName(name), PUBLIC, STATIC, FINAL)
                                .initializer(Objects.toString(defaultValueToSet))
                                .build());
                    }
                }
                return this;
            }

            @Override
            public TypeSpec.Builder build()
            {
                builder.addField(
                        FieldSpec.builder(int.class, "FIELD_COUNT", PRIVATE, STATIC, FINAL)
                                 .initializer(Integer.toString(nextIndex))
                                 .build())
                        .addField(
                        FieldSpec.builder(int.class, "lastFieldSet", PRIVATE)
                                 .initializer("-1")
                                 .build());
                return super.build();
            }
        }


        private static final class MemberFieldGenerator extends ClassSpecMixinGenerator
        {
            private MemberFieldGenerator(
                ClassName thisType,
                TypeSpec.Builder builder)
            {
                super(thisType, builder);
            }

            public MemberFieldGenerator addMember(
                String name,
                TypeName type,
                TypeName unsignedType,
                int size,
                String sizeName,
                boolean usedAsSize,
                AstByteOrder byteOrder)
            {
                if (usedAsSize && (isVarintType(type) || isVaruintType(type) || isVaruintnType(type)))
                {
                    builder.addField(FieldSpec.builder(TypeName.INT, dynamicValue(name), PRIVATE)
                            .build());
                }
                if (usedAsSize && !isVarintType(type))
                {
                    builder.addField(FieldSpec.builder(TypeName.INT, dynamicOffset(name), PRIVATE)
                           .build());
                }

                if (type.isPrimitive())
                {
                    if (size != -1 || sizeName != null)
                    {
                        builder.addField(FieldSpec.builder(TypeName.INT, dynamicOffset(name), PRIVATE)
                               .build());
                    }
                }
                else
                {
                    String fieldRW = String.format("%sRW", name);

                    if (TypeNames.DIRECT_BUFFER_TYPE.equals(type))
                    {
                        // skip
                    }
                    else if (type instanceof ParameterizedTypeName)
                    {
                        ParameterizedTypeName parameterizedType = (ParameterizedTypeName) type;
                        ClassName rawType = parameterizedType.rawType;
                        ClassName itemType = (ClassName) parameterizedType.typeArguments.get(0);
                        ClassName builderRawType = rawType.nestedClass("Builder");
                        ClassName itemBuilderType = itemType.nestedClass("Builder");
                        ParameterizedTypeName builderType = ParameterizedTypeName.get(builderRawType, itemBuilderType, itemType);

                        builder.addField(FieldSpec.builder(builderType, fieldRW, PRIVATE, FINAL)
                                .initializer("new $T(new $T(), new $T())", builderType, itemBuilderType, itemType)
                                .build());
                    }
                    else if (type instanceof ClassName)
                    {
                        ClassName classType = (ClassName) type;
                        TypeName builderType = classType.nestedClass("Builder");

                        if ((isString16Type(classType) || isString32Type(classType)) && byteOrder == NETWORK)
                        {
                            builder.addField(FieldSpec.builder(builderType, fieldRW, PRIVATE, FINAL)
                                    .initializer("new $T($T.BIG_ENDIAN)", builderType, ByteOrder.class)
                                    .build());
                        }
                        else
                        {
                            builder.addField(FieldSpec.builder(builderType, fieldRW, PRIVATE, FINAL)
                                    .initializer("new $T()", builderType)
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

            private boolean priorFieldIsAutomaticallySet;

            private MemberAccessorGenerator(
                ClassName thisType,
                TypeSpec.Builder builder)
            {
                super(thisType, builder);
            }

            public MemberAccessorGenerator addMember(
                String name,
                TypeName type,
                TypeName unsignedType,
                boolean usedAsSize,
                int size,
                String sizeName,
                Object defaultValue,
                String priorFieldIfDefaulted,
                Consumer<CodeBlock.Builder> defaultPriorField)
            {
                if (type instanceof ClassName)
                {
                    ClassName classType = (ClassName) type;
                    if (isStringType(classType))
                    {
                        addStringType(name,
                                      classType,
                                      priorFieldIfDefaulted,
                                      defaultPriorField);
                    }
                    else if ("OctetsFW".equals(classType.simpleName()))
                    {
                        addOctetsType(name,
                                classType,
                                size,
                                sizeName,
                                defaultValue,
                                priorFieldIfDefaulted,
                                defaultPriorField);
                    }
                }
                priorFieldIsAutomaticallySet = usedAsSize && !isVarintType(type) && !isVaruintType(type) && !isVaruintnType(type);
                return this;
            }

            private void addOctetsType(
                String name,
                ClassName className,
                int size,
                String sizeName,
                Object defaultValue,
                String priorFieldIfDefaulted,
                Consumer<CodeBlock.Builder> defaultPriorField)
            {
                String fieldRW = String.format("%sRW", name);
                ClassName builderType = className.nestedClass("Builder");

                CodeBlock.Builder code = CodeBlock.builder();
                if (priorFieldIfDefaulted != null)
                {
                    if (!priorFieldIsAutomaticallySet)
                    {
                        code.beginControlFlow("if (lastFieldSet < $L)", index(priorFieldIfDefaulted));
                    }
                    defaultPriorField.accept(code);
                    if (!priorFieldIsAutomaticallySet)
                    {
                        code.endControlFlow();
                    }
                }
                code.addStatement("assert lastFieldSet == $L - 1", index(name));

                if (size >= 0)
                {
                    code.addStatement("int newLimit = limit() + $L", size);
                    code.addStatement("checkLimit(newLimit, maxLimit())");
                    code.addStatement("return $L.wrap(buffer(), limit(), newLimit)", fieldRW);
                }
                else
                {
                    code.addStatement("return $L.wrap(buffer(), limit(), maxLimit())", fieldRW);
                }
                builder.addMethod(methodBuilder(methodName(name))
                        .addModifiers(PRIVATE)
                        .returns(builderType)
                        .addCode(code.build())
                        .build());
            }

            private void addStringType(
                String name,
                ClassName classType,
                String priorFieldIfDefaulted,
                Consumer<CodeBlock.Builder> defaultPriorField)
            {
                String fieldRW = String.format("%sRW", name);
                TypeName builderType = classType.nestedClass("Builder");
                CodeBlock.Builder code = CodeBlock.builder();
                if (priorFieldIfDefaulted != null)
                {
                    if (!priorFieldIsAutomaticallySet)
                    {
                        code.beginControlFlow("if (lastFieldSet < $L)", index(priorFieldIfDefaulted));
                    }
                    defaultPriorField.accept(code);
                    if (!priorFieldIsAutomaticallySet)
                    {
                        code.endControlFlow();
                    }
                }
                code.addStatement("assert lastFieldSet == $L - 1", index(name))
                    .addStatement("return $L.wrap(buffer(), limit(), maxLimit())", fieldRW);
                builder.addMethod(methodBuilder(methodName(name))
                        .addModifiers(PRIVATE)
                        .returns(builderType)
                        .addCode(code.build())
                        .build());
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
            private String priorRequiredField = null;
            private boolean priorFieldIsAutomaticallySet;

            private MemberMutatorGenerator(
                ClassName thisType,
                TypeResolver resolver,
                TypeSpec.Builder builder)
            {
                super(thisType, builder);
                this.resolver = resolver;
            }

            public MemberMutatorGenerator addMember(
                String name,
                AstType type,
                TypeName typeName,
                AstType unsignedType,
                TypeName unsignedTypeName,
                boolean usedAsSize,
                int size,
                String sizeName,
                TypeName sizeTypeName,
                AstByteOrder byteOrder,
                Object defaultValue,
                String priorFieldIfDefaulted,
                Consumer<CodeBlock.Builder> defaultPriorField)
            {
                boolean automaticallySet = usedAsSize &&
                        !isVarintType(typeName) && !isVaruintType(typeName) && !isVaruintnType(typeName);
                if (typeName.isPrimitive())
                {
                    if (sizeName != null)
                    {
                        addIntegerVariableArrayIteratorMutator(name, typeName, unsignedTypeName, sizeName, sizeTypeName,
                                defaultValue, priorFieldIfDefaulted, defaultPriorField);
                        addIntegerVariableArrayAppendMutator(name, type, typeName, unsignedTypeName, byteOrder,
                                sizeName, sizeTypeName, priorFieldIfDefaulted, defaultPriorField);
                    }
                    else if (size != -1)
                    {
                        addIntegerFixedArrayIteratorMutator(name, typeName, unsignedTypeName, size, priorFieldIfDefaulted,
                                defaultPriorField);
                        addIntegerFixedArrayAppendMutator(name, type, typeName, unsignedTypeName, byteOrder, size,
                                priorFieldIfDefaulted, defaultPriorField);
                    }
                    else
                    {
                        addPrimitiveMember(name, type, typeName, unsignedType, unsignedTypeName,
                                usedAsSize, byteOrder, priorFieldIfDefaulted, defaultPriorField);
                    }

                }
                else
                {
                    addNonPrimitiveMember(name, typeName, usedAsSize, size, sizeName, sizeTypeName, defaultValue,
                            priorFieldIfDefaulted, defaultPriorField);
                }
                priorFieldIsAutomaticallySet = automaticallySet;
                if (defaultValue == null && !isImplicitlyDefaulted(typeName, size, sizeName, type, resolver) && !automaticallySet)
                {
                    priorRequiredField = name;
                }
                return this;
            }

            private void addPrimitiveMember(
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
                boolean automaticallySet = usedAsSize &&
                        !isVarintType(typeName) && !isVaruintType(typeName) && !isVaruintnType(typeName);
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

            private void addIntegerFixedArrayIteratorMutator(
                String name,
                TypeName type,
                TypeName unsignedType,
                int size,
                String priorFieldIfDefaulted,
                Consumer<CodeBlock.Builder> defaultPriorField)
            {
                CodeBlock.Builder code = CodeBlock.builder();
                if (priorRequiredField != null)
                {
                    code.addStatement("assert lastFieldSet >= $L", index(priorRequiredField));
                }
                TypeName inputType = (unsignedType != null) ? unsignedType : type;
                TypeName valueType = inputType == TypeName.LONG ? TypeName.LONG : TypeName.INT;
                TypeName iteratorType = inputType == TypeName.LONG ? LONG_ITERATOR_CLASS_NAME
                        : INT_ITERATOR_CLASS_NAME;
                if (defaultPriorField != null)
                {
                    generateDefaultPriorField(priorFieldIfDefaulted, defaultPriorField, code);
                }
                code.beginControlFlow("if (values == null)");
                code.addStatement("throw new $T($S)",
                        IllegalArgumentException.class, format("fixed size array %s cannot be set to null", name));
                code.endControlFlow();
                code.addStatement("int count = 0");
                code.beginControlFlow("while (values.hasNext())");
                code.addStatement("$T value = values.next$L()", valueType,
                        valueType == TypeName.LONG ? "Long" : "Int");
                code.add("$[");
                code.add("$L(", appendMethodName(name));
                if (valueType != type)
                {
                    code.add("($T)", inputType);

                    if (unsignedType != null)
                    {
                        if (type == TypeName.BYTE)
                        {
                            code.add("(value & 0xFF))");
                        }
                        else if (type == TypeName.SHORT)
                        {
                            code.add("(value & 0xFFFF))");
                        }
                        else if (type == TypeName.INT)
                        {
                            code.add("(value & 0xFFFF_FFFFL))");
                        }
                    }
                    else
                    {
                        code.add("value)");
                    }
                }
                else
                {
                    code.add("value)");
                }
                code.add(";\n$]");
                code.addStatement("count++");
                code.endControlFlow();
                code.beginControlFlow("if (count < $L)", arraySize(name));
                code.addStatement("throw new $T($S)",
                        IllegalArgumentException.class, format("Not enough values for %s", name));
                code.endControlFlow();
                code.addStatement("return this");

                builder.addMethod(methodBuilder(methodName(name))
                        .addModifiers(PUBLIC)
                        .addParameter(iteratorType, "values")
                        .returns(thisType)
                        .addCode(code.build())
                        .build());
            }

            private void addIntegerFixedArrayAppendMutator(
                String name,
                AstType type,
                TypeName typeName,
                TypeName unsignedTypeName,
                AstByteOrder byteOrder,
                int size,
                String priorFieldIfDefaulted,
                Consumer<CodeBlock.Builder> defaultPriorField)
            {
                String putterName = PUTTER_NAMES.get(typeName);
                if (putterName == null)
                {
                    throw new IllegalStateException("member type not supported: " + typeName);
                }

                CodeBlock.Builder code = CodeBlock.builder();
                if (priorRequiredField != null)
                {
                    code.addStatement("assert lastFieldSet >= $L", index(priorRequiredField));
                }
                if (unsignedTypeName != null)
                {
                    generateUnsignedIntRangeCheck(name, typeName, code);
                }

                code.beginControlFlow("if ($L == -1)", dynamicOffset(name));

                if (defaultPriorField != null)
                {
                    generateDefaultPriorField(priorFieldIfDefaulted, defaultPriorField, code);
                }
                code.addStatement("assert lastFieldSet == $L - 1", index(name))
                    .addStatement("$L = limit()", dynamicOffset(name))
                    .endControlFlow();

                code.addStatement("int newLimit = limit() + $L", size(name))
                    .addStatement("checkLimit(newLimit, maxLimit())")
                    .addStatement("int newSize = (newLimit - $L) / $L",  dynamicOffset(name), size(name))
                    .beginControlFlow("if (newSize == $L)", arraySize(name))
                        .addStatement("lastFieldSet = $L", index(name))
                    .endControlFlow();

                TypeName inputType = (unsignedTypeName != null) ? unsignedTypeName : typeName;
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

                    if (inputType != typeName)
                    {
                        code.add("($T)", typeName);

                        if (unsignedTypeName != null)
                        {
                            if (typeName == TypeName.BYTE)
                            {
                                code.add("(value & 0xFF)");
                            }
                            else if (typeName == TypeName.SHORT)
                            {
                                code.add("(value & 0xFFFF)");
                            }
                            else if (typeName == TypeName.INT)
                            {
                                code.add("(value & 0xFFFF_FFFFL)", ByteOrder.class);
                            }
                            else
                            {
                                code.add("value");
                            }
                        }
                        else
                        {
                            code.add("value");
                        }
                    }
                    else
                    {
                        code.add("value");
                    }
                    if (byteOrder == NETWORK)
                    {
                        code.add(", $T.BIG_ENDIAN", ByteOrder.class);
                    }
                    code.add(");\n$]");
                }
                code.addStatement("limit(newLimit)")
                    .addStatement("return this");

                builder.addMethod(methodBuilder(appendMethodName(name))
                        .addModifiers(PUBLIC)
                        .addParameter(inputType, "value")
                        .returns(thisType)
                        .addCode(code.build())
                        .build());
            }

            private void addIntegerVariableArrayIteratorMutator(
                String name,
                TypeName type,
                TypeName unsignedType,
                String sizeName,
                TypeName sizeType,
                Object defaultValue,
                String priorFieldIfDefaulted,
                Consumer<CodeBlock.Builder> defaultPriorField)
            {
                CodeBlock.Builder code = CodeBlock.builder();
                if (priorRequiredField != null)
                {
                    code.addStatement("assert lastFieldSet >= $L", index(priorRequiredField));
                }
                code.addStatement("assert lastFieldSet <= $L", index(name));
                TypeName inputType = (unsignedType != null) ? unsignedType : type;
                TypeName valueType = inputType == TypeName.LONG ? TypeName.LONG : TypeName.INT;
                TypeName iteratorType = inputType == TypeName.LONG ? LONG_ITERATOR_CLASS_NAME
                        : INT_ITERATOR_CLASS_NAME;
                if (defaultPriorField != null)
                {
                    generateDefaultPriorField(priorFieldIfDefaulted, defaultPriorField, code);
                }
                if (defaultValue != null)
                {
                    code.beginControlFlow("if (values == null || !values.hasNext())");
                    code.addStatement("int limit = limit()");
                    code.addStatement("limit($L)", dynamicOffset(sizeName));
                    code.add("$[");
                    code.add("$L(values == null ? ", methodName(sizeName));
                    if (sizeType == TypeName.BYTE || sizeType == TypeName.SHORT)
                    {
                        code.add("($T) ", sizeType);
                    }
                    code.add("-1 : 0)");
                    code.add(";\n$]");
                }
                else
                {
                    code.beginControlFlow("if (values == null)");
                    code.addStatement("throw new $T($S + $S)",
                            IllegalArgumentException.class, name, " does not default to null so cannot be set to null");
                    code.endControlFlow();
                    code.beginControlFlow("if (!values.hasNext())");
                    code.addStatement("int limit = limit()");
                    code.addStatement("limit($L)", dynamicOffset(sizeName));
                    code.addStatement("$L(0)", methodName(sizeName));
                }
                code.addStatement("limit(limit)");
                code.addStatement("assert lastFieldSet == $L - 1", index(name));
                code.addStatement("lastFieldSet = $L", index(name));
                code.nextControlFlow("else");
                code.beginControlFlow("while (values.hasNext())");
                code.addStatement("$T value = values.next$L()", valueType,
                        valueType == TypeName.LONG ? "Long" : "Int");
                code.add("$[");
                code.add("$L(", appendMethodName(name));
                if (valueType != type)
                {
                    code.add("($T)", inputType);

                    if (unsignedType != null)
                    {
                        if (type == TypeName.BYTE)
                        {
                            code.add("(value & 0xFF))");
                        }
                        else if (type == TypeName.SHORT)
                        {
                            code.add("(value & 0xFFFF))");
                        }
                        else if (type == TypeName.INT)
                        {
                            code.add("(value & 0xFFFF_FFFFL))");
                        }
                    }
                    else
                    {
                        code.add("value)");
                    }
                }
                else
                {
                    code.add("value)");
                }
                code.add(";\n$]");
                code.endControlFlow();
                code.endControlFlow();
                code.addStatement("return this");

                builder.addMethod(methodBuilder(methodName(name))
                        .addModifiers(PUBLIC)
                        .addParameter(iteratorType, "values")
                        .returns(thisType)
                        .addCode(code.build())
                        .build());
            }

            private void addIntegerVariableArrayAppendMutator(
                String name,
                AstType type,
                TypeName typeName,
                TypeName unsignedTypeName,
                AstByteOrder byteOrder,
                String sizeName,
                TypeName sizeType,
                String priorFieldIfDefaulted,
                Consumer<CodeBlock.Builder> defaultPriorField)
            {
                String putterName = PUTTER_NAMES.get(typeName);
                if (putterName == null)
                {
                    throw new IllegalStateException("member type not supported: " + typeName);
                }

                CodeBlock.Builder code = CodeBlock.builder();
                if (priorRequiredField != null)
                {
                    code.addStatement("assert lastFieldSet >= $L", index(priorRequiredField));
                }
                code.addStatement("assert lastFieldSet <= $L", index(name));
                if (unsignedTypeName != null)
                {
                    generateUnsignedIntRangeCheck(name, typeName, code);
                }

                code.beginControlFlow("if (lastFieldSet < $L)", index(name));

                if (defaultPriorField != null)
                {
                    generateDefaultPriorField(priorFieldIfDefaulted, defaultPriorField, code);
                }
                code.addStatement("assert lastFieldSet == $L - 1", index(name))
                    .addStatement("$L = limit()", dynamicOffset(name))
                    .addStatement("lastFieldSet = $L", index(name))
                    .endControlFlow();

                code.addStatement("int newLimit = limit() + $L", size(name))
                    .addStatement("checkLimit(newLimit, maxLimit())");

                TypeName inputType = (unsignedTypeName != null) ? unsignedTypeName : typeName;
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

                    if (inputType != typeName)
                    {
                        code.add("($T)", typeName);

                        if (typeName == TypeName.BYTE)
                        {
                            code.add("(value & 0xFF)");
                        }
                        else if (typeName == TypeName.SHORT)
                        {
                            code.add("(value & 0xFFFF)");
                        }
                        else if (typeName == TypeName.INT)
                        {
                            code.add("(value & 0xFFFF_FFFFL)");
                        }
                        else
                        {
                            code.add("value");
                        }
                    }
                    else
                    {
                        code.add("value");
                    }
                    if (byteOrder == NETWORK)
                    {
                        code.add(", $T.BIG_ENDIAN", ByteOrder.class);
                    }
                    code.add(");\n$]");
                }
                code.addStatement("lastFieldSet = $L", index(name))
                    .addStatement("limit($L)", dynamicOffset(sizeName))
                    .addStatement("int newSize = (newLimit - $L) / $L", dynamicOffset(name), size(name));

                code.add("$[");
                code.add("$L(", methodName(sizeName));
                if (sizeType == TypeName.BYTE || sizeType == TypeName.SHORT)
                {
                    code.add("($T) ", sizeType);
                }
                code.add("newSize)");
                code.add(";\n$]");

                code.addStatement("limit(newLimit)")
                    .addStatement("return this");

                builder.addMethod(methodBuilder(appendMethodName(name))
                        .addModifiers(PUBLIC)
                        .addParameter(inputType, "value")
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

            private void generateDefaultPriorField(String priorFieldIfDefaulted, Consumer<CodeBlock.Builder> defaultPriorField,
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

            private void addNonPrimitiveMember(
                String name,
                TypeName type,
                boolean usedAsSize,
                int size,
                String sizeName,
                TypeName sizeType,
                Object defaultValue,
                String priorDefaulted,
                Consumer<CodeBlock.Builder> defaultPriorField)
            {
                if (type instanceof ClassName)
                {
                    ClassName className = (ClassName) type;
                    addClassType(name, className, usedAsSize, size, sizeName, sizeType, defaultValue, priorDefaulted,
                            defaultPriorField);
                }
                else if (type instanceof ParameterizedTypeName)
                {
                    ParameterizedTypeName parameterizedType = (ParameterizedTypeName) type;
                    addParameterizedType(name, parameterizedType, priorDefaulted, defaultPriorField);
                }
                else
                {
                    // TODO: throw exception? I don't think we should ever get here
                    builder.addMethod(methodBuilder(methodName(name))
                           .addModifiers(PUBLIC)
                           .returns(thisType)
                           .addParameter(type, "value")
                           .addStatement("$LRW.set(value)", name)
                           .addStatement("return this")
                           .build());
                }
            }

            private void addClassType(
                String name,
                ClassName className,
                boolean usedAsSize,
                int size,
                String sizeName,
                TypeName sizeType,
                Object defaultValue,
                String priorFieldIfDefaulted,
                Consumer<CodeBlock.Builder> defaultPriorField)
            {
                if (isStringType(className))
                {
                    addStringType(className, name);
                }
                else if (DIRECT_BUFFER_TYPE.equals(className))
                {
                    // TODO: What IDL type does this correspond to? I don't see it in TypeResolver
                    // so I suspect this is dead code and should be removed
                    addDirectBufferType(name);
                }
                else if ("OctetsFW".equals(className.simpleName()))
                {
                    addOctetsType(className, name, size, sizeName, sizeType, defaultValue);
                }
                else
                {
                    ClassName consumerType = ClassName.get(Consumer.class);
                    ClassName builderType = className.nestedClass("Builder");
                    TypeName parameterType = isVarint32Type(className) ? TypeName.INT
                        : isVarint64Type(className) ? TypeName.LONG
                        : isVaruint32Type(className) ? TypeName.INT
                        : ParameterizedTypeName.get(consumerType, builderType);
                    String parameterName = isVarintType(className) || isVaruintType(className) || isVaruintnType(className)
                            ? "value"
                            : "mutator";

                    CodeBlock.Builder code = CodeBlock.builder();
                    if (priorFieldIfDefaulted != null)
                    {
                        code.beginControlFlow("if (lastFieldSet < $L)", index(priorFieldIfDefaulted));
                        defaultPriorField.accept(code);
                        code.endControlFlow();
                    }
                    code.addStatement("assert lastFieldSet == $L - 1", index(name))
                        .addStatement("$T $LRW = this.$LRW.wrap(buffer(), limit(), maxLimit())", builderType, name, name);
                    if (isVarintType(className) || isVaruintType(className) || isVaruintnType(className))
                    {
                        code.addStatement("$LRW.set($L)", name, parameterName);
                        if (usedAsSize)
                        {
                            code.addStatement("$L = $L", dynamicValue(name), parameterName);
                        }
                    }
                    else
                    {
                        code.addStatement("$L.accept($LRW)", parameterName, name);
                    }
                    code.addStatement("limit($LRW.build().limit())", name)
                        .addStatement("lastFieldSet = $L", index(name))
                        .addStatement("return this");

                    builder.addMethod(methodBuilder(methodName(name))
                            .addModifiers(PUBLIC)
                            .returns(thisType)
                            .addParameter(parameterType, parameterName)
                            .addCode(code.build())
                            .build());

                    code = CodeBlock.builder();
                    if (priorFieldIfDefaulted != null)
                    {
                        code.beginControlFlow("if (lastFieldSet < $L)", index(priorFieldIfDefaulted));
                        defaultPriorField.accept(code);
                        code.endControlFlow();
                    }
                    code.addStatement("assert lastFieldSet == $L - 1", index(name))
                        .addStatement("int newLimit = limit() + field.sizeof()")
                        .addStatement("checkLimit(newLimit, maxLimit())")
                        .addStatement("buffer().putBytes(limit(), field.buffer(), field.offset(), field.sizeof())")
                        .addStatement("limit(newLimit)")
                        .addStatement("lastFieldSet = $L", index(name))
                        .addStatement("return this");
                    builder.addMethod(methodBuilder(methodName(name))
                        .addModifiers(PUBLIC)
                        .returns(thisType)
                        .addParameter(className, "field")
                        .addCode(code.build())
                        .build());
                }
            }



            private void addOctetsType(
                ClassName className,
                String name,
                int size,
                String sizeName,
                TypeName sizeType,
                Object defaultValue)
            {
                addOctetsOctetsFWMutator(className, name, size, sizeName, sizeType, defaultValue);
                addOctetsConsumerBuilderMutator(className, name, size, sizeName, sizeType);
                addOctetsBufferMutator(className, name, size, sizeName, sizeType);
            }

            private void addOctetsOctetsFWMutator(
                ClassName className,
                String name,
                int size,
                String sizeName,
                TypeName sizeType,
                Object defaultValue)
            {
                ClassName builderType = className.nestedClass("Builder");
                CodeBlock.Builder code = CodeBlock.builder();
                if (size >= 0)
                {
                    code.addStatement("$T $LRW = $L()", builderType, name, methodName(name))
                        .addStatement("$LRW.set(value)", name)
                        .addStatement("int expectedLimit = $LRW.maxLimit()", name)
                        .addStatement("int actualLimit = $LRW.build().limit()", name)
                        .beginControlFlow("if (actualLimit != expectedLimit)")
                        .addStatement("throw new IllegalStateException(String.format($S, " +
                                      "actualLimit - limit(), expectedLimit - limit()))",
                            format("%%d instead of %%d bytes have been set for field \"%s\"", name))
                        .endControlFlow()
                        .addStatement("limit($LRW.maxLimit())", name);
                }
                else if (sizeName != null)
                {
                    code.addStatement("int size$$")
                        .addStatement("int newLimit")
                        .addStatement("$T $LRW = $L()", builderType, name, methodName(name));
                    if (isVaruintType(sizeType) || isVaruintnType(sizeType))
                    {
                        code.beginControlFlow("if (value == null)")
                                .addStatement("size$$ = 0")
                                .addStatement("newLimit = limit()")
                                .nextControlFlow("else")
                                .addStatement("$LRW.set(value)", name)
                                .addStatement("newLimit = $LRW.build().limit()", name)
                                .addStatement("size$$ = newLimit - limit()")
                                .endControlFlow();
                    }
                    else if (defaultValue == NULL_DEFAULT)
                    {
                        code.beginControlFlow("if (value == null)")
                            .addStatement("size$$ = -1")
                            .addStatement("newLimit = limit()")
                            .nextControlFlow("else")
                            .addStatement("$LRW.set(value)", name)
                            .addStatement("newLimit = $LRW.build().limit()", name)
                            .addStatement("size$$ = newLimit - limit()")
                            .endControlFlow();
                    }
                    else
                    {
                        code.beginControlFlow("if (value == null)")
                            .addStatement("throw new IllegalArgumentException($S)",
                                    format("value cannot be null for field \"%s\" that does not default to null", name))
                            .endControlFlow()
                            .addStatement("$LRW.set(value)", name)
                            .addStatement("newLimit = $LRW.build().limit()", name)
                            .addStatement("size$$ = newLimit - limit()");
                    }

                    if (isVarintType(sizeType) || isVaruintType(sizeType) || isVaruintnType(sizeType))
                    {
                        code.beginControlFlow("if (size$$ != $L)", dynamicValue(sizeName))
                            .addStatement("throw new IllegalStateException(String.format($S, size$$, $L, $S))",
                                format("%%d bytes have been set for field \"%s\", does not match value %%d set in %%s",
                                        name),
                                dynamicValue(sizeName),
                                sizeName)
                            .endControlFlow();
                    }
                    else
                    {
                        code.addStatement("limit($L)", dynamicOffset(sizeName))
                            .addStatement("$L(size$$)", sizeName);
                    }
                    code.addStatement("limit(newLimit)");
                }
                else
                {
                    code.addStatement("$T $LRW = $L()", builderType, name, methodName(name))
                        .addStatement("$LRW.set(value)", name)
                        .addStatement("limit($LRW.build().limit())", name);
                }
                code.addStatement("lastFieldSet = $L", index(name))
                    .addStatement("return this");

                builder.addMethod(methodBuilder(methodName(name))
                        .addModifiers(PUBLIC)
                        .returns(thisType)
                        .addParameter(className, "value")
                        .addCode(code.build())
                        .build());
            }

            private void addOctetsConsumerBuilderMutator(
                ClassName className,
                String name,
                int size,
                String sizeName,
                TypeName sizeType)
            {
                ClassName builderType = className.nestedClass("Builder");
                ClassName consumerType = ClassName.get(Consumer.class);
                TypeName mutatorType = ParameterizedTypeName.get(consumerType, builderType);
                CodeBlock.Builder code = CodeBlock.builder();
                code.addStatement("$T $LRW = $L()", builderType, name, methodName(name))
                    .addStatement("mutator.accept($LRW)", name);
                if (size >= 0)
                {
                    code.addStatement("int expectedLimit = $LRW.maxLimit()", name)
                        .addStatement("int actualLimit = $LRW.build().limit()", name)
                        .beginControlFlow("if (actualLimit != expectedLimit)")
                        .addStatement("throw new IllegalStateException(String.format($S, " +
                                      "actualLimit - limit(), expectedLimit - limit()))",
                            format("%%d instead of %%d bytes have been set for field \"%s\"", name))
                        .endControlFlow();
                    code.addStatement("limit($LRW.maxLimit())", name);
                }
                else if (sizeName != null)
                {
                    code.addStatement("int newLimit = $LRW.build().limit()", name)
                        .addStatement("int size$$ = newLimit - limit()");

                    if (isVarintType(sizeType) || isVaruintType(sizeType) || isVaruintnType(sizeType))
                    {
                        code.beginControlFlow("if (size$$ != $L)", dynamicValue(sizeName))
                            .addStatement("throw new IllegalStateException(String.format($S, size$$, $L, $S))",
                                format("%%d bytes have been set for field \"%s\", does not match value %%d set in %%s",
                                        name),
                                dynamicValue(sizeName),
                                sizeName)
                            .endControlFlow();
                    }
                    else
                    {
                        code.addStatement("limit($L)", dynamicOffset(sizeName))
                            .addStatement("$L(size$$)", sizeName);
                    }
                    code.addStatement("limit(newLimit)");
                }
                else
                {
                    code.addStatement("limit($LRW.build().limit())", name);
                }
                code.addStatement("lastFieldSet = $L", index(name))
                    .addStatement("return this");

                builder.addMethod(methodBuilder(methodName(name))
                        .addModifiers(PUBLIC)
                        .returns(thisType)
                        .addParameter(mutatorType, "mutator")
                        .addCode(code.build())
                        .build());
            }

            private void addOctetsBufferMutator(
                ClassName className,
                String name,
                int size,
                String sizeName,
                TypeName sizeType)
            {
                ClassName builderType = className.nestedClass("Builder");
                CodeBlock.Builder code = CodeBlock.builder();
                code.addStatement("$T $LRW = $L()", builderType, name, methodName(name));
                if (size >= 0)
                {
                    code.addStatement("int fieldSize = $LRW.maxLimit() - limit()", name)
                        .beginControlFlow("if (length != fieldSize)")
                        .addStatement("throw new IllegalArgumentException(String.format($S, length, fieldSize))",
                           format("Invalid length %%d for field \"%s\", expected %%d", name))
                        .endControlFlow();
                }
                code.addStatement("$LRW.set(buffer, offset, length)", name);
                if (sizeName != null)
                {
                    code.addStatement("int newLimit = $LRW.build().limit()", name)
                        .addStatement("int size$$ = newLimit - limit()");

                    if (isVarintType(sizeType) || isVaruintType(sizeType) || isVaruintnType(sizeType))
                    {
                        code.beginControlFlow("if (size$$ != $L)", dynamicValue(sizeName))
                            .addStatement("throw new IllegalStateException(String.format($S, size$$, $L, $S))",
                               format("%%d bytes have been set for field \"%s\", does not match value %%d set in %%s",
                                       name),
                               dynamicValue(sizeName),
                               sizeName)
                            .endControlFlow();
                    }
                    else
                    {
                        code.addStatement("limit($L)", dynamicOffset(sizeName))
                            .addStatement("$L(size$$)", sizeName);
                    }
                    code.addStatement("limit(newLimit)");
                }
                else
                {
                    code.addStatement("limit($LRW.build().limit())", name);
                }
                code.addStatement("lastFieldSet = $L", index(name))
                    .addStatement("return this");

                builder.addMethod(methodBuilder(methodName(name))
                       .addModifiers(PUBLIC)
                       .returns(thisType)
                       .addParameter(DIRECT_BUFFER_TYPE, "buffer")
                       .addParameter(int.class, "offset")
                       .addParameter(int.class, "length")
                       .addCode(code.build())
                       .build());
            }

            private void addStringType(
                ClassName className,
                String name)
            {
                ClassName builderType = className.nestedClass("Builder");

                builder.addMethod(methodBuilder(methodName(name))
                        .addModifiers(PUBLIC)
                        .returns(thisType)
                        .addParameter(String.class, "value")
                        .addStatement("$T $LRW = $L()", builderType, name, methodName(name))
                        .addStatement("$LRW.set(value, $T.UTF_8)", name, StandardCharsets.class)
                        .addStatement("lastFieldSet = $L", index(name))
                        .addStatement("limit($LRW.build().limit())", name)
                        .addStatement("return this")
                        .build());

                builder.addMethod(methodBuilder(methodName(name))
                        .addModifiers(PUBLIC)
                        .returns(thisType)
                        .addParameter(className, "value")
                        .addStatement("$T $LRW = $L()", builderType, name, methodName(name))
                        .addStatement("$LRW.set(value)", name)
                        .addStatement("lastFieldSet = $L", index(name))
                        .addStatement("limit($LRW.build().limit())", name)
                        .addStatement("return this")
                        .build());

                builder.addMethod(methodBuilder(methodName(name))
                        .addModifiers(PUBLIC)
                        .returns(thisType)
                        .addParameter(DIRECT_BUFFER_TYPE, "buffer")
                        .addParameter(int.class, "offset")
                        .addParameter(int.class, "length")
                        .addStatement("$T $LRW = $L()", builderType, name, methodName(name))
                        .addStatement("$LRW.set(buffer, offset, length)", name)
                        .addStatement("lastFieldSet = $L", index(name))
                        .addStatement("limit($LRW.build().limit())", name)
                        .addStatement("return this")
                        .build());
            }

            private void addDirectBufferType(
                String name)
            {
                // TODO: revise/remove this once I understand when/if this would get called
                builder.addMethod(methodBuilder(methodName(name))
                        .addModifiers(PUBLIC)
                        .addParameter(IntUnaryOperator.class, "mutator")
                        .addParameter(IntConsumer.class, "error")
                        .returns(thisType)
                        .addStatement("int length = mutator.applyAsInt(offset() + $L)", offset(name))
                        .beginControlFlow("if (length < 0)")
                        .addStatement("error.accept(length)")
                        .addStatement("limit(offset() + $L)", offset(name))
                        .nextControlFlow("else")
                        .addStatement("limit(offset() + $L + length)", offset(name))
                        .endControlFlow()
                        .addStatement("return this")
                        .build());

                builder.addMethod(methodBuilder(methodName(name))
                        .addModifiers(PUBLIC)
                        .addParameter(IntUnaryOperator.class, "mutator")
                        .returns(thisType)
                        .addStatement("int length = mutator.applyAsInt(offset() + $L)", offset(name))
                        .beginControlFlow("if (length < 0)")
                        .addStatement("throw new IllegalStateException()")
                        .endControlFlow()
                        .addStatement("limit(offset() + $L + length)", offset(name))
                        .addStatement("return this")
                        .build());

                builder.addMethod(methodBuilder(methodName(name))
                        .addModifiers(PUBLIC)
                        .addParameter(DIRECT_BUFFER_TYPE, "value")
                        .addParameter(int.class, "offset")
                        .addParameter(int.class, "length")
                        .returns(thisType)
                        .addStatement("buffer().putBytes(offset() + $L, value, offset, length)", offset(name))
                        .addStatement("limit(offset() + $L + length)", offset(name))
                        .addStatement("return this")
                        .build());

                builder.addMethod(methodBuilder(methodName(name))
                        .addModifiers(PUBLIC)
                        .addParameter(DIRECT_BUFFER_TYPE, "value")
                        .returns(thisType)
                        .addStatement("buffer().putBytes(offset() + $L, value, 0, value.capacity())", offset(name))
                        .addStatement("limit(offset() + $L + value.capacity())", offset(name))
                        .addStatement("return this")
                        .build());

                builder.addMethod(methodBuilder(methodName(name))
                        .addModifiers(PUBLIC)
                        .addParameter(BYTE_ARRAY, "value")
                        .addParameter(int.class, "offset")
                        .addParameter(int.class, "length")
                        .returns(thisType)
                        .addStatement("buffer().putBytes(offset() + $L, value, offset, length)", offset(name))
                        .addStatement("limit(offset() + $L + length)", offset(name))
                        .addStatement("return this")
                        .build());

                builder.addMethod(methodBuilder(methodName(name))
                        .addModifiers(PUBLIC)
                        .addParameter(BYTE_ARRAY, "value")
                        .returns(thisType)
                        .addStatement("buffer().putBytes(offset() + $L, value, 0, value.length)", offset(name))
                        .addStatement("limit(offset() + $L + value.length)", offset(name))
                        .addStatement("return this")
                        .build());

                builder.addMethod(methodBuilder(methodName(name))
                        .addModifiers(PUBLIC)
                        .addParameter(String.class, "value")
                        .returns(thisType)
                        .addStatement("int length = buffer().putStringWithoutLengthUtf8(offset() + $L, value)", offset(name))
                        .addStatement("limit(offset() + $L + length)", offset(name))
                        .addStatement("return this")
                        .build());
            }

            private void addParameterizedType(
                String name,
                ParameterizedTypeName parameterizedType,
                String priorFieldIfDefaulted,
                Consumer<CodeBlock.Builder> defaultPriorField)
            {
                ClassName rawType = parameterizedType.rawType;
                ClassName itemType = (ClassName) parameterizedType.typeArguments.get(0);
                ClassName builderRawType = rawType.nestedClass("Builder");
                ClassName itemBuilderType = itemType.nestedClass("Builder");
                ParameterizedTypeName builderType = ParameterizedTypeName.get(builderRawType, itemBuilderType, itemType);

                ClassName consumerType = ClassName.get(Consumer.class);
                TypeName mutatorType = ParameterizedTypeName.get(consumerType, builderType);

                CodeBlock.Builder code = CodeBlock.builder();
                if (priorFieldIfDefaulted != null)
                {
                    code.beginControlFlow("if (lastFieldSet < $L)", index(priorFieldIfDefaulted));
                    defaultPriorField.accept(code);
                    code.endControlFlow();
                }
                code.addStatement("assert lastFieldSet == $L - 1", index(name))
                    .addStatement("$T $LRW = this.$LRW.wrap(buffer(), limit(), maxLimit())", builderType, name, name)
                    .addStatement("mutator.accept($LRW)", name)
                    .addStatement("limit($LRW.build().limit())", name)
                    .addStatement("lastFieldSet = $L", index(name))
                    .addStatement("return this");

                builder.addMethod(methodBuilder(methodName(name))
                        .addModifiers(PUBLIC)
                        .returns(thisType)
                        .addParameter(mutatorType, "mutator")
                        .addCode(code.build())
                        .build());

                if ("Array32FW".equals(rawType.simpleName()))
                {
                    code = CodeBlock.builder();
                    if (priorFieldIfDefaulted != null)
                    {
                        code.beginControlFlow("if (lastFieldSet < $L)", index(priorFieldIfDefaulted));
                        defaultPriorField.accept(code);
                        code.endControlFlow();
                    }
                    code.addStatement("assert lastFieldSet == $L - 1", index(name))
                        .addStatement("int newLimit = limit() + field.sizeof()")
                        .addStatement("checkLimit(newLimit, maxLimit())")
                        .addStatement("buffer().putBytes(limit(), field.buffer(), field.offset(), field.sizeof())")
                        .addStatement("limit(newLimit)")
                        .addStatement("lastFieldSet = $L", index(name))
                        .addStatement("return this");
                    builder.addMethod(methodBuilder(methodName(name))
                        .addModifiers(PUBLIC)
                        .returns(thisType)
                        .addParameter(ParameterizedTypeName.get(rawType, itemType), "field")
                        .addCode(code.build())
                        .build());

                    // Add a method to append list items
                    code = CodeBlock.builder();
                    if (priorFieldIfDefaulted != null)
                    {
                        code.beginControlFlow("if (lastFieldSet < $L)", index(priorFieldIfDefaulted));
                        defaultPriorField.accept(code);
                        code.endControlFlow();
                    }
                    code.addStatement("assert lastFieldSet >= $L - 1", index(name))
                        .beginControlFlow("if (lastFieldSet < $L)", index(name))
                            .addStatement("$LRW.wrap(buffer(), limit(), maxLimit())", name)
                        .endControlFlow()
                        .addStatement("$LRW.item(mutator)", name)
                        .addStatement("limit($LRW.build().limit())", name)
                        .addStatement("lastFieldSet = $L", index(name))
                        .addStatement("return this");

                    TypeName itemMutatorType = ParameterizedTypeName.get(consumerType, itemBuilderType);
                    builder.addMethod(methodBuilder(methodName(name + "Item"))
                            .addModifiers(PUBLIC)
                            .returns(thisType)
                            .addParameter(itemMutatorType, "mutator")
                            .addCode(code.build())
                            .build());
                }
            }
        }

        private final class WrapMethodGenerator extends MethodSpecGenerator
        {

            private WrapMethodGenerator(
                ClassName thisType,
                TypeSpec.Builder builder)
            {
                super(methodBuilder("wrap")
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .addParameter(MUTABLE_DIRECT_BUFFER_TYPE, "buffer")
                        .addParameter(int.class, "offset")
                        .addParameter(int.class, "maxLimit")
                        .returns(thisName)
                        .addStatement("super.wrap(buffer, offset, maxLimit)"));
            }

            public WrapMethodGenerator addMember(
                String name,
                TypeName type,
                TypeName unsignedType,
                boolean usedAsSize,
                int size,
                String sizeName,
                TypeName sizeType,
                AstByteOrder byteOrder,
                Object defaultValue,
                String priorFieldIfDefaulted,
                Consumer<CodeBlock.Builder> defaultPriorField)
            {

                if (usedAsSize && !isVarintType(type) && !isVaruintType(type) && !isVaruintnType(type) ||
                    type.isPrimitive() && (size != -1 || sizeName != null))
                {
                    builder.addStatement("$L = -1", dynamicOffset(name));
                }
                return this;
            }

            @Override
            public MethodSpec generate()
            {
                return builder.addStatement("lastFieldSet = -1")
                              .addStatement("limit(offset)")
                              .addStatement("return this")
                              .build();
            }
        }

        private final class WrapMethodWithArrayGenerator extends MethodSpecGenerator
        {
            private final TypeName arrayBuilderType;

            private WrapMethodWithArrayGenerator(
                ClassName structType,
                TypeResolver resolver)
            {
                super(methodBuilder("wrap")
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .returns(thisName)
                    .addStatement("super.wrap(array)"));

                ClassName arrayClassName = resolver.resolveClass(AstType.ARRAY);
                ClassName arrayBuilderClassName = arrayClassName.nestedClass("Builder");
                arrayBuilderType = ParameterizedTypeName.get(arrayBuilderClassName,
                        WildcardTypeName.subtypeOf(Object.class),
                        WildcardTypeName.subtypeOf(Object.class),
                        WildcardTypeName.subtypeOf(Object.class));
            }

            public WrapMethodWithArrayGenerator addMember(
                String name,
                TypeName type,
                boolean usedAsSize,
                int size,
                String sizeName)
            {

                if (usedAsSize && !isVarintType(type) && !isVaruintType(type) && !isVaruintnType(type) ||
                    type.isPrimitive() && (size != -1 || sizeName != null))
                {
                    builder.addStatement("$L = -1", dynamicOffset(name));
                }
                return this;
            }

            @Override
            public MethodSpec generate()
            {
                return builder.addParameter(arrayBuilderType, "array")
                    .addStatement("lastFieldSet = -1")
                    .addStatement("return this")
                    .build();
            }
        }
    }

    private static boolean isOctetsType(
        TypeName type)
    {
        return type instanceof ClassName && "OctetsFW".equals(((ClassName) type).simpleName());
    }

    private static boolean isStringType(
        ClassName classType)
    {
        return isString8Type(classType) || isString16Type(classType) || isString32Type(classType) || isVarStringType(classType);
    }

    private static boolean isString8Type(
        ClassName classType)
    {
        String name = classType.simpleName();
        return "String8FW".equals(name);
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

    private static boolean isVaruintType(
        TypeName type)
    {
        return type instanceof ClassName && "Varuint32FW".equals(((ClassName) type).simpleName());
    }

    private static boolean isVaruintnType(
        TypeName type)
    {
        return type instanceof ClassName && "Varuint32nFW".equals(((ClassName) type).simpleName());
    }

    private static boolean isVarintType(
        TypeName type)
    {
        return type instanceof ClassName && "Varint32FW".equals(((ClassName) type).simpleName()) ||
                type instanceof ClassName && "Varint64FW".equals(((ClassName) type).simpleName());
    }

    private static boolean isVaruint32Type(
        TypeName type)
    {
        return type instanceof ClassName && "Varuint32FW".equals(((ClassName) type).simpleName()) ||
                type instanceof ClassName && "Varuint32nFW".equals(((ClassName) type).simpleName());
    }

    private static boolean isVarint32Type(
        TypeName type)
    {
        return type instanceof ClassName && "Varint32FW".equals(((ClassName) type).simpleName());
    }

    private static boolean isVarint64Type(
        TypeName type)
    {
        return type instanceof ClassName && "Varint64FW".equals(((ClassName) type).simpleName());
    }

    private static boolean isStringType(
        TypeName type)
    {
        return type instanceof ClassName && ("String8FW".equals(((ClassName) type).simpleName()) ||
                                             "String16FW".equals(((ClassName) type).simpleName()) ||
                                             "String32FW".equals(((ClassName) type).simpleName()) ||
                                             "VarStringFW".equals(((ClassName) type).simpleName()));
    }

    private static boolean isEnumType(
        Kind kind)
    {
        return Kind.ENUM.equals(kind);
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

    private static String arraySize(
        String fieldName)
    {
        return String.format("ARRAY_SIZE_%s", constant(fieldName));
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

    private static String constant(
        String fieldName)
    {
        return fieldName.replaceAll("([^_A-Z])([A-Z])", "$1_$2").toUpperCase();
    }

    private static String dynamicLimit(String fieldName)
    {
        return "limit" + initCap(fieldName);
    }

    private static String iterator(String fieldName)
    {
        return "iterator" + initCap(fieldName);
    }

    private static ClassName iteratorClass(
        ClassName structName,
        TypeName type,
        TypeName unsignedType)
    {
        TypeName generateType = (unsignedType != null) ? unsignedType : type;
        return generateType == TypeName.LONG ? structName.nestedClass("LongPrimitiveIterator")
                : structName.nestedClass("IntPrimitiveIterator");
    }

    private static String methodName(String name)
    {
        return RESERVED_METHOD_NAMES.contains(name) ? name + "$" : name;
    }
}
