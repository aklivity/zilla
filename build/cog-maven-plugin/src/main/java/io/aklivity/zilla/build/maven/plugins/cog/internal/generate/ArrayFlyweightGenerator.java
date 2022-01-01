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
package io.aklivity.zilla.build.maven.plugins.cog.internal.generate;

import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeName.BOOLEAN;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static io.aklivity.zilla.build.maven.plugins.cog.internal.generate.TypeNames.DIRECT_BUFFER_TYPE;
import static io.aklivity.zilla.build.maven.plugins.cog.internal.generate.TypeNames.MUTABLE_DIRECT_BUFFER_TYPE;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import java.nio.ByteOrder;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import com.squareup.javapoet.WildcardTypeName;

public class ArrayFlyweightGenerator extends ParameterizedTypeSpecGenerator
{
    private final TypeSpec.Builder classBuilder;
    private final BuilderClassBuilder builderClassBuilder;

    public ArrayFlyweightGenerator(
        ClassName flyweightType)
    {
        this(flyweightType, "ArrayFW");
    }

    protected ArrayFlyweightGenerator(
        ClassName flyweightType,
        String className)
    {
        super(ParameterizedTypeName.get(flyweightType.peerClass(className), TypeVariableName.get("T")));

        TypeVariableName typeVarT = TypeVariableName.get("T");
        TypeVariableName itemType = typeVarT.withBounds(flyweightType);

        this.classBuilder = classBuilder(thisRawName).superclass(flyweightType)
                .addTypeVariable(itemType).addModifiers(PUBLIC, FINAL);
        this.builderClassBuilder = new BuilderClassBuilder(thisName, flyweightType.nestedClass("Builder"));
    }

    @Override
    public TypeSpec generate()
    {
        return classBuilder
                .addField(fieldSizeLengthConstant())
                .addField(fieldByteOrder())
                .addField(fieldItemRO())
                .addMethod(constructor())
                .addMethod(constructorByteOrder())
                .addMethod(limitMethod())
                .addMethod(tryWrapMethod())
                .addMethod(wrapMethod())
                .addMethod(forEachMethod())
                .addMethod(anyMatchMethod())
                .addMethod(matchFirstMethod())
                .addMethod(isEmptyMethod())
                .addMethod(toStringMethod())
                .addMethod(length0Method())
                .addType(builderClassBuilder.build())
                .build();
    }

    private FieldSpec fieldSizeLengthConstant()
    {
        return FieldSpec.builder(int.class, "FIELD_SIZE_LENGTH", PRIVATE, STATIC, FINAL)
                .initializer("$T.BYTES", Integer.class)
                .build();
    }

    private FieldSpec fieldByteOrder()
    {
        return FieldSpec.builder(ByteOrder.class, "byteOrder", PRIVATE, FINAL)
                .build();
    }

    private FieldSpec fieldItemRO()
    {
        TypeName itemType = thisName.typeArguments.get(0);
        return FieldSpec.builder(itemType, "itemRO", PRIVATE, FINAL)
                .build();
    }

    private MethodSpec constructor()
    {
        TypeName itemType = thisName.typeArguments.get(0);
        return constructorBuilder()
                .addModifiers(PUBLIC)
                .addParameter(itemType, "itemRO")
                .addStatement("this.itemRO = $T.requireNonNull(itemRO)", Objects.class)
                .addStatement("this.byteOrder = $T.nativeOrder()", ByteOrder.class)
                .build();
    }

    private MethodSpec constructorByteOrder()
    {
        TypeName itemType = thisName.typeArguments.get(0);
        return constructorBuilder()
                .addModifiers(PUBLIC)
                .addParameter(itemType, "itemRO")
                .addParameter(ByteOrder.class, "byteOrder")
                .addStatement("this.itemRO = $T.requireNonNull(itemRO)", Objects.class)
                .addStatement("this.byteOrder = byteOrder")
                .build();
    }

    private MethodSpec limitMethod()
    {
        return methodBuilder("limit")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(int.class)
                .addStatement("return offset() + FIELD_SIZE_LENGTH + length0()")
                .build();
    }

    private MethodSpec tryWrapMethod()
    {
        return methodBuilder("tryWrap")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .addParameter(DIRECT_BUFFER_TYPE, "buffer")
                .addParameter(int.class, "offset")
                .addParameter(int.class, "maxLimit")
                .returns(thisName)
                .beginControlFlow("if (null == super.tryWrap(buffer, offset, maxLimit) || offset + FIELD_SIZE_LENGTH > maxLimit)")
                .addStatement("return null")
                .endControlFlow()
                .beginControlFlow("if (length0() < 0)")
                .addStatement("throw new $T(String.format($S, offset))", IllegalArgumentException.class,
                        "Invalid list at offset %d: size < 0")
                .endControlFlow()
                .beginControlFlow("if (limit() > maxLimit)")
                .addStatement("return null")
                .endControlFlow()
                .addStatement("return this")
                .build();
    }

    private MethodSpec wrapMethod()
    {
        return methodBuilder("wrap")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .addParameter(DIRECT_BUFFER_TYPE, "buffer")
                .addParameter(int.class, "offset")
                .addParameter(int.class, "maxLimit")
                .returns(thisName)
                .addStatement("super.wrap(buffer, offset, maxLimit)")
                .addStatement("checkLimit(offset + FIELD_SIZE_LENGTH, maxLimit)")
                .beginControlFlow("if (length0() < 0)")
                .addStatement("throw new $T(String.format($S, offset))", IllegalArgumentException.class,
                        "Invalid list at offset %d: size < 0")
                .endControlFlow()
                .addStatement("checkLimit(limit(), maxLimit)")
                .addStatement("return this")
                .build();
    }

    private MethodSpec forEachMethod()
    {
        ClassName consumerRawType = ClassName.get(Consumer.class);
        TypeName tType = thisName.typeArguments.get(0);
        TypeName itemType = WildcardTypeName.supertypeOf(tType);
        TypeName consumerType = ParameterizedTypeName.get(consumerRawType, itemType);

        return methodBuilder("forEach")
                .addModifiers(PUBLIC)
                .addParameter(consumerType, "consumer")
                .returns(thisName)
                .beginControlFlow("for (int offset = offset() + FIELD_SIZE_LENGTH; offset < limit(); offset = itemRO.limit())")
                .addStatement("itemRO.wrap(buffer(), offset, limit())")
                .addStatement("consumer.accept(itemRO)")
                .endControlFlow()
                .addStatement("return this")
                .build();
    }

    private MethodSpec anyMatchMethod()
    {
        ClassName predicateRawType = ClassName.get(Predicate.class);
        TypeName itemType = WildcardTypeName.supertypeOf(thisName.typeArguments.get(0));
        TypeName consumerType = ParameterizedTypeName.get(predicateRawType, itemType);
        return methodBuilder("anyMatch")
              .addModifiers(PUBLIC)
              .addParameter(consumerType, "predicate")
              .returns(BOOLEAN)
              .beginControlFlow("for (int offset = offset() + FIELD_SIZE_LENGTH; offset < limit(); offset = itemRO.limit())")
              .addStatement("itemRO.wrap(buffer(), offset, maxLimit())")
                  .beginControlFlow("if (predicate.test(itemRO))")
                      .addStatement("return true")
                  .endControlFlow()
              .endControlFlow()
              .addStatement("return false")
              .build();
    }

    private MethodSpec matchFirstMethod()
    {
        ClassName predicateRawType = ClassName.get(Predicate.class);
        TypeName tType = thisName.typeArguments.get(0);
        TypeName itemType = WildcardTypeName.supertypeOf(tType);
        TypeName consumerType = ParameterizedTypeName.get(predicateRawType, itemType);
        return methodBuilder("matchFirst")
                .addModifiers(PUBLIC)
                .addParameter(consumerType, "predicate")
                .returns(tType)
                .beginControlFlow("for (int offset = offset() + FIELD_SIZE_LENGTH; offset < limit(); offset = itemRO.limit())")
                .addStatement("itemRO.wrap(buffer(), offset, maxLimit())")
                .beginControlFlow("if (predicate.test(itemRO))")
                .addStatement("return itemRO")
                .endControlFlow()
                .endControlFlow()
                .addStatement("return null")
                .build();
    }

    private MethodSpec isEmptyMethod()
    {
        return methodBuilder("isEmpty")
              .addModifiers(PUBLIC)
              .returns(BOOLEAN)
              .addStatement("return length0() == 0")
              .build();
    }

    private MethodSpec toStringMethod()
    {
        return methodBuilder("toString")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(String.class)
                .addStatement("return String.format($S, length0())", "ARRAY containing %d bytes of data")
                .build();
    }

    private MethodSpec length0Method()
    {
        return methodBuilder("length0")
                .addModifiers(PRIVATE)
                .returns(int.class)
                .addStatement("return buffer().getInt(offset(), byteOrder)")
                .build();
    }

    private static final class BuilderClassBuilder
    {
        private final TypeSpec.Builder classBuilder;
        private final ParameterizedTypeName classType;
        private final ParameterizedTypeName enclosingType;
        private final TypeVariableName typeVarB;
        private TypeVariableName typeVarT;

        private BuilderClassBuilder(
            ParameterizedTypeName enclosingType,
            ClassName builderRawType)
        {
            TypeName builderType = ParameterizedTypeName.get(builderRawType, enclosingType);
            ClassName flyweightType = builderRawType.enclosingClassName();
            ClassName classRawType = enclosingType.rawType.nestedClass("Builder");

            this.typeVarB = TypeVariableName.get("B");
            this.typeVarT = TypeVariableName.get("T");
            this.enclosingType = enclosingType;
            this.classType = ParameterizedTypeName.get(classRawType, typeVarB, typeVarT);
            this.classBuilder = classBuilder(classType.rawType)
                    .addModifiers(PUBLIC, STATIC, FINAL)
                    .addTypeVariable(typeVarB.withBounds(ParameterizedTypeName.get(builderRawType, typeVarT)))
                    .addTypeVariable(typeVarT.withBounds(flyweightType))
                    .superclass(builderType);
        }

        public TypeSpec build()
        {
            return classBuilder
                    .addField(fieldByteOrder())
                    .addField(fieldItemRW())
                    .addMethod(constructor())
                    .addMethod(constructorByteOrder())
                    .addMethod(wrapMethod())
                    .addMethod(itemMethod())
                    .addMethod(buildMethod())
                    .build();
        }

        private FieldSpec fieldByteOrder()
        {
            return FieldSpec.builder(ByteOrder.class, "byteOrder", PRIVATE, FINAL)
                    .build();
        }

        private FieldSpec fieldItemRW()
        {
            return FieldSpec.builder(typeVarB, "itemRW", PRIVATE, FINAL)
                    .build();
        }

        private MethodSpec constructor()
        {
            return constructorBuilder()
                    .addModifiers(PUBLIC)
                    .addParameter(typeVarB, "itemRW")
                    .addParameter(typeVarT, "itemRO")
                    .addStatement("super(new $T(itemRO))", enclosingType)
                    .addStatement("this.byteOrder = $T.nativeOrder()", ByteOrder.class)
                    .addStatement("this.itemRW = itemRW")
                    .build();
        }

        private MethodSpec constructorByteOrder()
        {
            return constructorBuilder()
                    .addModifiers(PUBLIC)
                    .addParameter(typeVarB, "itemRW")
                    .addParameter(typeVarT, "itemRO")
                    .addParameter(ByteOrder.class, "byteOrder")
                    .addStatement("super(new $T(itemRO))", enclosingType)
                    .addStatement("this.byteOrder = byteOrder")
                    .addStatement("this.itemRW = itemRW")
                    .build();
        }

        private MethodSpec wrapMethod()
        {
            return methodBuilder("wrap")
                    .addModifiers(PUBLIC)
                    .returns(classType)
                    .addParameter(MUTABLE_DIRECT_BUFFER_TYPE, "buffer")
                    .addParameter(int.class, "offset")
                    .addParameter(int.class, "maxLimit")
                    .addStatement("super.wrap(buffer, offset, maxLimit)")
                    .addStatement("int newLimit = offset + FIELD_SIZE_LENGTH")
                    .addStatement("checkLimit(newLimit, maxLimit)")
                    .addStatement("super.limit(newLimit)")
                    .addStatement("return this")
                    .build();
        }

        private MethodSpec itemMethod()
        {
            ClassName consumerRawType = ClassName.get(Consumer.class);
            TypeName mutatorType = ParameterizedTypeName.get(consumerRawType, typeVarB);

            return methodBuilder("item")
                    .addModifiers(PUBLIC)
                    .returns(classType)
                    .addParameter(mutatorType, "mutator")
                    .addStatement("itemRW.wrap(buffer(), limit(), maxLimit())")
                    .addStatement("mutator.accept(itemRW)")
                    .addStatement("limit(itemRW.build().limit())")
                    .addStatement("return this")
                    .build();
        }

        private MethodSpec buildMethod()
        {
            return methodBuilder("build")
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .returns(enclosingType)
                    .addStatement("int sizeInBytes = limit() - offset() - FIELD_SIZE_LENGTH")
                    .addStatement("buffer().putInt(offset(), sizeInBytes, byteOrder)")
                    .addStatement("return super.build()")
                    .build();
        }
    }
}
