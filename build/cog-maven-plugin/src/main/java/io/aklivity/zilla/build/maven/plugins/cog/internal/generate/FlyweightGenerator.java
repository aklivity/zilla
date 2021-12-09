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
package io.aklivity.zilla.build.maven.plugins.cog.internal.generate;

import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static io.aklivity.zilla.build.maven.plugins.cog.internal.generate.TypeNames.DIRECT_BUFFER_TYPE;
import static io.aklivity.zilla.build.maven.plugins.cog.internal.generate.TypeNames.MUTABLE_DIRECT_BUFFER_TYPE;
import static io.aklivity.zilla.build.maven.plugins.cog.internal.generate.TypeNames.UNSAFE_BUFFER_TYPE;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import java.util.function.Consumer;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import com.squareup.javapoet.WildcardTypeName;

public final class FlyweightGenerator extends ClassSpecGenerator
{
    private final TypeSpec.Builder classBuilder;
    private final ParameterizedTypeName arrayAnyType;
    private final BuilderClassBuilder builderClassBuilder;

    public FlyweightGenerator(
        ClassName flyweightType,
        ParameterizedTypeName arrayType)
    {
        super(flyweightType);

        this.classBuilder = classBuilder(thisName).addModifiers(PUBLIC, ABSTRACT);
        this.arrayAnyType = ParameterizedTypeName.get(arrayType.rawType, WildcardTypeName.subtypeOf(Object.class));
        this.builderClassBuilder = new BuilderClassBuilder(thisName, arrayAnyType);
    }

    @Override
    public TypeSpec generate()
    {
        return classBuilder
            .addField(bufferField())
            .addField(emptyBytesField())
            .addField(offsetField())
            .addField(maxLimitField())
            .addField(compareBufferField())
            .addMethod(offsetMethod())
            .addMethod(bufferMethod())
            .addMethod(limitMethod())
            .addMethod(sizeofMethod())
            .addMethod(maxLimitMethod())
            .addMethod(tryWrapMethod())
            .addMethod(wrapMethod())
            .addMethod(wrapWithArrayMethod())
            .addMethod(checkLimitMethod())
            .addMethod(equalsMethod())
            .addMethod(hashCodeMethod())
            .addType(visitorInterface())
            .addType(builderClassBuilder.build())
            .build();
    }

    private TypeSpec visitorInterface()
    {
        TypeVariableName typeVarT = TypeVariableName.get("T");
        return TypeSpec.interfaceBuilder(thisName.nestedClass("Visitor"))
                .addModifiers(PUBLIC)
                .addTypeVariable(typeVarT)
                .addAnnotation(FunctionalInterface.class)
                .addMethod(MethodSpec.methodBuilder("visit")
                        .addModifiers(PUBLIC, ABSTRACT)
                        .returns(typeVarT)
                        .addParameter(DIRECT_BUFFER_TYPE, "buffer")
                        .addParameter(int.class, "offset")
                        .addParameter(int.class, "maxLimit")
                        .build())
                .build();
    }

    private FieldSpec bufferField()
    {
        return FieldSpec.builder(DIRECT_BUFFER_TYPE, "buffer", PRIVATE).build();
    }

    private FieldSpec offsetField()
    {
        return FieldSpec.builder(int.class, "offset", PRIVATE).build();
    }

    private FieldSpec maxLimitField()
    {
        return FieldSpec.builder(int.class, "maxLimit", PRIVATE).build();
    }

    private FieldSpec compareBufferField()
    {
        return FieldSpec.builder(UNSAFE_BUFFER_TYPE, "compareBuffer", PRIVATE)
                .initializer("new UnsafeBuffer(EMPTY_BYTES)")
                .build();
    }

    private FieldSpec emptyBytesField()
    {
        return FieldSpec.builder(byte[].class, "EMPTY_BYTES", PRIVATE, STATIC)
                .initializer("new byte[0]")
                .build();
    }

    private MethodSpec maxLimitMethod()
    {
        return methodBuilder("maxLimit")
                  .addModifiers(PROTECTED, FINAL)
                  .returns(int.class)
                  .addStatement("return maxLimit")
                  .build();
    }

    private MethodSpec offsetMethod()
    {
        return methodBuilder("offset")
                  .addModifiers(PUBLIC, FINAL)
                  .returns(int.class)
                  .addStatement("return offset")
                  .build();
    }

    private MethodSpec bufferMethod()
    {
        return methodBuilder("buffer")
                  .addModifiers(PUBLIC, FINAL)
                  .returns(DIRECT_BUFFER_TYPE)
                  .addStatement("return buffer")
                  .build();
    }

    private MethodSpec limitMethod()
    {
        return methodBuilder("limit")
                  .addModifiers(PUBLIC, ABSTRACT)
                  .returns(int.class)
                  .build();
    }

    private MethodSpec sizeofMethod()
    {
        return methodBuilder("sizeof")
                  .addModifiers(PUBLIC, FINAL)
                  .returns(int.class)
                  .addStatement("return limit() - offset()")
                  .build();
    }

    private MethodSpec tryWrapMethod()
    {
        return methodBuilder("tryWrap")
                  .addModifiers(PUBLIC)
                  .addParameter(DIRECT_BUFFER_TYPE, "buffer")
                  .addParameter(int.class, "offset")
                  .addParameter(int.class, "maxLimit")
                  .returns(thisName)
                  .beginControlFlow("if (offset > maxLimit)")
                  .addStatement("return null")
                  .endControlFlow()
                  .addStatement("this.buffer = buffer")
                  .addStatement("this.offset = offset")
                  .addStatement("this.maxLimit = maxLimit")
                  .addStatement("return this")
                  .build();
    }

    private MethodSpec wrapMethod()
    {
        return methodBuilder("wrap")
                  .addModifiers(PUBLIC)
                  .addParameter(DIRECT_BUFFER_TYPE, "buffer")
                  .addParameter(int.class, "offset")
                  .addParameter(int.class, "maxLimit")
                  .returns(thisName)
                  .beginControlFlow("if (offset > maxLimit)")
                  .addStatement("final String msg = String.format(\"offset=%d is beyond maxLimit=%d\", " +
                          "offset, maxLimit)")
                  .addStatement("throw new IndexOutOfBoundsException(msg)")
                  .endControlFlow()
                  .addStatement("this.buffer = buffer")
                  .addStatement("this.offset = offset")
                  .addStatement("this.maxLimit = maxLimit")
                  .addStatement("return this")
                  .build();
    }

    private MethodSpec wrapWithArrayMethod()
    {
        return methodBuilder("wrap")
            .addModifiers(PUBLIC)
            .addParameter(DIRECT_BUFFER_TYPE, "buffer")
            .addParameter(int.class, "offset")
            .addParameter(int.class, "maxLimit")
            .addParameter(arrayAnyType, "array")
            .returns(thisName)
            .addStatement("wrap(buffer, offset, maxLimit)")
            .addStatement("return this")
            .build();
    }

    private MethodSpec checkLimitMethod()
    {
        return methodBuilder("checkLimit")
                  .addModifiers(PROTECTED, STATIC, FINAL)
                  .addParameter(int.class, "limit")
                  .addParameter(int.class, "maxLimit")
                  .beginControlFlow("if (limit > maxLimit)")
                  .addStatement("final String msg = String.format(\"limit=%d is beyond maxLimit=%d\", " +
                          "limit, maxLimit)")
                  .addStatement("throw new IndexOutOfBoundsException(msg)")
                  .endControlFlow()
                  .build();
    }

    private MethodSpec equalsMethod()
    {
        return methodBuilder("equals")
                  .addAnnotation(Override.class)
                  .addModifiers(PUBLIC)
                  .addParameter(Object.class, "obj")
                  .returns(boolean.class)
                  .beginControlFlow("if (this == obj)")
                  .addStatement("return true")
                  .nextControlFlow("else if (obj == null || !(obj instanceof Flyweight))")
                  .addStatement("return false")
                  .nextControlFlow("else")
                  .addStatement("Flyweight that = (Flyweight) obj")
                  .addStatement("compareBuffer.wrap(buffer, offset, sizeof())")
                  .addStatement("that.compareBuffer.wrap(that.buffer, that.offset, that.sizeof())")
                  .addStatement("return compareBuffer.equals(that.compareBuffer)")
                  .endControlFlow()
                  .build();
    }

    private MethodSpec hashCodeMethod()
    {
        return methodBuilder("hashCode")
                  .addAnnotation(Override.class)
                  .addModifiers(PUBLIC)
                  .returns(int.class)
                  .addStatement("int result = 1")
                  .beginControlFlow("for (int i = offset; i < limit(); i++)")
                  .addStatement("result = 31 * result + buffer.getByte(i)")
                  .endControlFlow()
                  .addStatement("return result")
                  .build();
    }

    private static final class BuilderClassBuilder
    {
        private final TypeSpec.Builder classBuilder;
        private final ClassName thisRawName;
        private final ParameterizedTypeName arrayAnyType;
        private final ParameterizedTypeName thisName;
        private final TypeVariableName typeVarT;

        private BuilderClassBuilder(
            ClassName flyweightType,
            ParameterizedTypeName arrayAnyType)
        {
            this.thisRawName = flyweightType.nestedClass("Builder");
            this.arrayAnyType = arrayAnyType;

            this.typeVarT = TypeVariableName.get("T");
            this.thisName = ParameterizedTypeName.get(thisRawName, typeVarT);

            this.classBuilder = classBuilder(thisRawName.simpleName())
                    .addTypeVariable(typeVarT.withBounds(flyweightType))
                    .addModifiers(PUBLIC, ABSTRACT, STATIC);
        }

        public TypeSpec build()
        {
            return classBuilder
                .addField(flyweightField())
                .addField(bufferField())
                .addField(offsetField())
                .addField(limitField())
                .addField(maxLimitField())
                .addMethod(limitAccessor())
                .addMethod(maxLimitAccessor())
                .addMethod(buildMethod())
                .addMethod(resetMethod())
                .addMethod(rewrapMethod())
                .addMethod(constructor())
                .addMethod(flyweightAccessor())
                .addMethod(bufferAccessor())
                .addMethod(offsetAccessor())
                .addMethod(sizeofAccessor())
                .addMethod(limitMutator())
                .addMethod(wrapMethod())
                .addMethod(wrapWithArrayMethod())
                .addMethod(iterateMethod())
                .addMethod(rebuildMethod())
                .addMethod(setMethod())
                .addType(visitorInterface())
                .build();
        }

        private TypeSpec visitorInterface()
        {
            return TypeSpec.interfaceBuilder(thisRawName.nestedClass("Visitor"))
                    .addModifiers(PUBLIC)
                    .addAnnotation(FunctionalInterface.class)
                    .addMethod(MethodSpec.methodBuilder("visit")
                            .addModifiers(PUBLIC, ABSTRACT)
                            .addParameter(MUTABLE_DIRECT_BUFFER_TYPE, "buffer")
                            .addParameter(int.class, "offset")
                            .addParameter(int.class, "maxLimit")
                            .returns(int.class)
                            .build())
                    .build();
        }

        private FieldSpec flyweightField()
        {
            return FieldSpec.builder(typeVarT, "flyweight", PRIVATE, FINAL).build();
        }

        private FieldSpec bufferField()
        {
            return FieldSpec.builder(MUTABLE_DIRECT_BUFFER_TYPE, "buffer", PRIVATE).build();
        }

        private FieldSpec offsetField()
        {
            return FieldSpec.builder(int.class, "offset", PRIVATE).build();
        }

        private FieldSpec limitField()
        {
            return FieldSpec.builder(int.class, "limit", PRIVATE).build();
        }

        private FieldSpec maxLimitField()
        {
            return FieldSpec.builder(int.class, "maxLimit", PRIVATE).build();
        }

        private MethodSpec limitAccessor()
        {
            return methodBuilder("limit")
                      .addModifiers(PUBLIC, FINAL)
                      .returns(int.class)
                      .addStatement("return limit")
                      .build();
        }

        private MethodSpec maxLimitAccessor()
        {
            return methodBuilder("maxLimit")
                      .addModifiers(PUBLIC, FINAL)
                      .returns(int.class)
                      .addStatement("return maxLimit")
                      .build();
        }

        private MethodSpec buildMethod()
        {
            return methodBuilder("build")
                      .addModifiers(PUBLIC)
                      .returns(typeVarT)
                      .addStatement("flyweight.wrap(buffer, offset, limit)")
                      .addStatement("return flyweight")
                      .build();
        }

        private MethodSpec resetMethod()
        {
            ClassName arrayBuilderRawType = arrayAnyType.rawType.nestedClass("Builder");
            ParameterizedTypeName arrayBuilderType = ParameterizedTypeName.get(arrayBuilderRawType,
                    WildcardTypeName.subtypeOf(Object.class),
                    WildcardTypeName.subtypeOf(Object.class),
                    WildcardTypeName.subtypeOf(Object.class));
            return methodBuilder("reset")
                      .addModifiers(PUBLIC)
                      .addParameter(arrayBuilderType, "array")
                      .build();
        }

        private MethodSpec rewrapMethod()
        {
            return methodBuilder("rewrap")
                      .addModifiers(PUBLIC)
                      .returns(thisName)
                      .addStatement("this.limit = this.offset")
                      .addStatement("return this")
                      .build();
        }

        private MethodSpec constructor()
        {
            return constructorBuilder()
                      .addModifiers(PROTECTED)
                      .addParameter(typeVarT, "flyweight")
                      .addStatement("this.flyweight = flyweight")
                      .build();
        }

        private MethodSpec flyweightAccessor()
        {
            return methodBuilder("flyweight")
                      .addModifiers(PUBLIC, FINAL)
                      .returns(typeVarT)
                      .addStatement("return flyweight")
                      .build();
        }

        private MethodSpec bufferAccessor()
        {
            return methodBuilder("buffer")
                      .addModifiers(PUBLIC, FINAL)
                      .returns(MUTABLE_DIRECT_BUFFER_TYPE)
                      .addStatement("return buffer")
                      .build();
        }

        private MethodSpec offsetAccessor()
        {
            return methodBuilder("offset")
                      .addModifiers(PROTECTED, FINAL)
                      .returns(int.class)
                      .addStatement("return offset")
                      .build();
        }

        private MethodSpec sizeofAccessor()
        {
            return methodBuilder("sizeof")
                .addModifiers(PUBLIC)
                .returns(int.class)
                .addStatement("return limit - offset")
                .build();
        }

        private MethodSpec limitMutator()
        {
            return methodBuilder("limit")
                      .addModifiers(PROTECTED, FINAL)
                      .addParameter(int.class, "limit")
                      .addStatement("this.limit = limit")
                      .build();
        }

        private MethodSpec wrapMethod()
        {
            return methodBuilder("wrap")
                      .addModifiers(PUBLIC)
                      .returns(thisName)
                      .addParameter(MUTABLE_DIRECT_BUFFER_TYPE, "buffer")
                      .addParameter(int.class, "offset")
                      .addParameter(int.class, "maxLimit")
                      .addStatement("this.buffer = buffer")
                      .addStatement("this.offset = offset")
                      .addStatement("this.limit = offset")
                      .addStatement("this.maxLimit = maxLimit")
                      .addStatement("return this")
                      .build();
        }

        private MethodSpec wrapWithArrayMethod()
        {
            ClassName arrayBuilderRawType = arrayAnyType.rawType.nestedClass("Builder");
            ParameterizedTypeName arrayBuilderType = ParameterizedTypeName.get(arrayBuilderRawType,
                    WildcardTypeName.subtypeOf(Object.class),
                    WildcardTypeName.subtypeOf(Object.class),
                    WildcardTypeName.subtypeOf(Object.class));
            return methodBuilder("wrap")
                .addModifiers(PUBLIC)
                .returns(thisName)
                .addParameter(arrayBuilderType, "array")
                .addStatement("this.buffer = array.buffer()")
                .addStatement("this.offset = array.limit()")
                .addStatement("this.limit = array.limit()")
                .addStatement("this.maxLimit = array.maxLimit()")
                .addStatement("return this")
                .build();
        }

        private MethodSpec iterateMethod()
        {
            TypeVariableName typeVarE = TypeVariableName.get("E");
            ClassName consumerType = ClassName.get(Consumer.class);
            ClassName iterableType = ClassName.get(Iterable.class);

            return methodBuilder("iterate")
                    .addModifiers(PUBLIC)
                    .addTypeVariable(typeVarE)
                    .returns(thisName)
                    .addParameter(ParameterizedTypeName.get(iterableType, typeVarE), "iterable")
                    .addParameter(ParameterizedTypeName.get(consumerType, typeVarE), "action")
                    .addStatement("iterable.forEach(action)")
                    .addStatement("return this")
                    .build();
        }

        private MethodSpec rebuildMethod()
        {
            return methodBuilder("rebuild")
                .addModifiers(PUBLIC)
                .returns(typeVarT)
                .addParameter(typeVarT, "item")
                .addParameter(int.class, "maxLength")
                .addStatement("return item")
                .build();
        }

        private MethodSpec setMethod()
        {
            return methodBuilder("set")
                .addModifiers(PUBLIC)
                .returns(thisName)
                .addParameter(typeVarT, "value")
                .addStatement("buffer().putBytes(offset(), value.buffer(), value.offset(), value.sizeof())")
                .addStatement("limit(limit() + value.sizeof())")
                .addStatement("return this")
                .build();
        }
    }
}
