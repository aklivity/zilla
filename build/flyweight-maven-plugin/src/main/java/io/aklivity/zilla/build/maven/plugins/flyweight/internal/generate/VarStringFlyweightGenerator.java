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
import static io.aklivity.zilla.build.maven.plugins.flyweight.internal.generate.TypeNames.BIT_UTIL_TYPE;
import static io.aklivity.zilla.build.maven.plugins.flyweight.internal.generate.TypeNames.DIRECT_BUFFER_TYPE;
import static io.aklivity.zilla.build.maven.plugins.flyweight.internal.generate.TypeNames.MUTABLE_DIRECT_BUFFER_TYPE;
import static io.aklivity.zilla.build.maven.plugins.flyweight.internal.generate.TypeNames.UNSAFE_BUFFER_TYPE;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

public final class VarStringFlyweightGenerator extends ClassSpecGenerator
{
    private final ClassName varintType;
    private final TypeSpec.Builder classBuilder;
    private final BuilderClassBuilder builderClassBuilder;

    public VarStringFlyweightGenerator(
        ClassName stringType)
    {
        super(stringType.peerClass("VarStringFW"));

        this.varintType = stringType.peerClass("Varuint32nFW");
        this.classBuilder = classBuilder(thisName).superclass(stringType).addModifiers(PUBLIC, FINAL);
        this.builderClassBuilder = new BuilderClassBuilder(stringType, thisName,
                stringType.nestedClass("Builder"), varintType, varintType.nestedClass("Builder"));
    }

    @Override
    public TypeSpec generate()
    {
        return classBuilder
                .addField(fieldMaxSizeLengthConstant())
                .addField(lengthField())
                .addField(valueField())
                .addMethod(constructor())
                .addMethod(constructorString())
                .addMethod(constructorStringAndCharset())
                .addMethod(fieldSizeLengthMethod())
                .addMethod(limitMethod())
                .addMethod(valueMethod())
                .addMethod(asStringMethod())
                .addMethod(tryWrapMethod())
                .addMethod(wrapMethod())
                .addMethod(toStringMethod())
                .addMethod(lengthMethod())
                .addType(builderClassBuilder.build())
                .build();
    }

    private FieldSpec fieldMaxSizeLengthConstant()
    {
        return FieldSpec.builder(int.class, "FIELD_MAX_SIZE_LENGTH", PRIVATE, STATIC, FINAL)
                        .initializer("$T.SIZE_OF_INT + 1", BIT_UTIL_TYPE)
                        .build();
    }

    private FieldSpec lengthField()
    {
        return FieldSpec.builder(varintType, "lengthRO", PRIVATE, FINAL)
                        .initializer("new $T()", varintType)
                        .build();
    }

    private FieldSpec valueField()
    {
        return FieldSpec.builder(DIRECT_BUFFER_TYPE, "valueRO", PRIVATE, FINAL)
                        .initializer("new $T(0L, 0)", UNSAFE_BUFFER_TYPE)
                        .build();
    }

    private MethodSpec constructor()
    {
        return constructorBuilder()
                .addModifiers(PUBLIC)
                .build();
    }

    private MethodSpec constructorString()
    {
        return MethodSpec.constructorBuilder()
                         .addModifiers(PUBLIC)
                         .addParameter(String.class, "value")
                         .addStatement("this(value, $T.UTF_8)", StandardCharsets.class)
                         .build();
    }

    private MethodSpec constructorStringAndCharset()
    {
        return MethodSpec.constructorBuilder()
                         .addModifiers(PUBLIC)
                         .addParameter(String.class, "value")
                         .addParameter(Charset.class, "charset")
                         .addStatement("final byte[] encoded = value != null ? value.getBytes(charset) : null")
                         .addStatement("final int encodedSize = encoded != null ? encoded.length : 1")
                         .addStatement("final $T buffer = new $T(new byte[FIELD_MAX_SIZE_LENGTH + encodedSize])",
                                 MUTABLE_DIRECT_BUFFER_TYPE, UNSAFE_BUFFER_TYPE)
                         .addStatement("new Builder().wrap(buffer, 0, buffer.capacity()).set(value, charset).build();")
                         .build();
    }

    private MethodSpec fieldSizeLengthMethod()
    {
        return methodBuilder("fieldSizeLength")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(int.class)
                .addStatement("return lengthRO.sizeof()")
                .build();
    }

    private MethodSpec limitMethod()
    {
        return methodBuilder("limit")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(int.class)
                .addStatement("return lengthRO.limit() + Math.max(length(), 0)")
                .build();
    }

    private MethodSpec asStringMethod()
    {
        return methodBuilder("asString")
                .addModifiers(PUBLIC)
                .addAnnotation(Override.class)
                .returns(String.class)
                .beginControlFlow("if (maxLimit() == offset() || length() == -1)")
                .addStatement("return null")
                .endControlFlow()
                .addStatement("return buffer().getStringWithoutLengthUtf8(lengthRO.limit(), length())")
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
                .beginControlFlow("if (null == super.tryWrap(buffer, offset, maxLimit) || " +
                    "null == lengthRO.tryWrap(buffer, offset, maxLimit) || " +
                    "limit() > maxLimit)")
                .addStatement("return null")
                .endControlFlow()
                .addStatement("int length = length()")
                .beginControlFlow("if (length != -1)")
                .addStatement("valueRO.wrap(buffer, lengthRO.limit(), length)")
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
                .addStatement("lengthRO.wrap(buffer, offset, maxLimit)")
                .addStatement("checkLimit(limit(), maxLimit)")
                .addStatement("int length = length()")
                .beginControlFlow("if (length != -1)")
                .addStatement("valueRO.wrap(buffer, lengthRO.limit(), length)")
                .endControlFlow()
                .addStatement("return this")
                .build();
    }

    private MethodSpec valueMethod()
    {
        return methodBuilder("value")
                .addModifiers(PUBLIC)
                .returns(DIRECT_BUFFER_TYPE)
                .addStatement("return length() == -1 ? null : valueRO")
                .build();
    }

    private MethodSpec toStringMethod()
    {
        return methodBuilder("toString")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(String.class)
                .addStatement("return maxLimit() == offset() ? \"null\" : String.format(\"\\\"%s\\\"\", asString())")
                .build();
    }

    private MethodSpec lengthMethod()
    {
        return methodBuilder("length")
                .addModifiers(PUBLIC)
                .returns(int.class)
                .addStatement("int length = lengthRO.value()")
                .addStatement("return length < 0 ? -1 : length")
                .build();
    }

    private static final class BuilderClassBuilder
    {
        private final TypeSpec.Builder classBuilder;
        private final ClassName classType;
        private final ClassName stringType;
        private final ClassName varStringType;
        private final ClassName varintType;
        private final ClassName varintBuilderType;

        private BuilderClassBuilder(
            ClassName stringType,
            ClassName varStringType,
            ClassName builderRawType,
            ClassName varintType,
            ClassName varintBuilderType)
        {
            TypeName builderType = ParameterizedTypeName.get(builderRawType, varStringType);

            this.stringType = stringType;
            this.varStringType = varStringType;
            this.classType = varStringType.nestedClass("Builder");
            this.classBuilder = classBuilder(classType.simpleName())
                .addModifiers(PUBLIC, STATIC, FINAL)
                .superclass(builderType);
            this.varintType = varintType;
            this.varintBuilderType = varintBuilderType;
        }

        public TypeSpec build()
        {
            return classBuilder
                    .addField(lengthField())
                    .addField(valueSetField())
                    .addMethod(constructor())
                    .addMethod(wrapMethod())
                    .addMethod(setMethod())
                    .addMethod(setDirectBufferMethod())
                    .addMethod(setStringMethod())
                    .addMethod(checkLengthMethod())
                    .addMethod(buildMethod())
                    .build();
        }

        private FieldSpec lengthField()
        {
            return FieldSpec.builder(varintBuilderType, "lengthRW", PRIVATE)
                            .initializer("new $T()", varintBuilderType)
                            .build();
        }

        private FieldSpec valueSetField()
        {
            return FieldSpec.builder(boolean.class, "valueSet", PRIVATE)
                            .build();
        }

        private MethodSpec constructor()
        {
            return constructorBuilder()
                    .addModifiers(PUBLIC)
                    .addStatement("super(new $T())", varStringType)
                    .build();
        }

        private MethodSpec wrapMethod()
        {
            return methodBuilder("wrap")
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .returns(classType)
                    .addParameter(MUTABLE_DIRECT_BUFFER_TYPE, "buffer")
                    .addParameter(int.class, "offset")
                    .addParameter(int.class, "maxLimit")
                    .addStatement("checkLimit(offset + 1, maxLimit)")
                    .addStatement("super.wrap(buffer, offset, maxLimit)")
                    .addStatement("lengthRW.wrap(buffer, offset, maxLimit)")
                    .addStatement("this.valueSet = false")
                    .addStatement("return this")
                    .build();
        }

        private MethodSpec setMethod()
        {
            return methodBuilder("set")
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .returns(classType)
                    .addParameter(stringType, "value")
                    .addStatement("int len = value.length()")
                    .beginControlFlow("if (len == -1)")
                    .addStatement("limit(lengthRW.set(-1).build().limit())")
                    .nextControlFlow("else")
                    .addStatement("$T length = lengthRW.set(len).build()", varintType)
                    .addStatement("int newLimit = length.limit() + length.value()")
                    .addStatement("checkLimit(newLimit, maxLimit())")
                    .addStatement("buffer().putBytes(length.limit(), value.buffer(), " +
                            "value.offset() + value.fieldSizeLength(), len)")
                    .addStatement("limit(newLimit)")
                    .endControlFlow()
                    .addStatement("valueSet = true")
                    .addStatement("return this")
                    .build();
        }

        private MethodSpec setDirectBufferMethod()
        {
            return methodBuilder("set")
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .returns(classType)
                    .addParameter(DIRECT_BUFFER_TYPE, "srcBuffer")
                    .addParameter(int.class, "srcOffset")
                    .addParameter(int.class, "srcLength")
                    .addStatement("checkLength(srcLength)")
                    .addStatement("$T length = lengthRW.set(srcLength).build()", varintType)
                    .addStatement("int newLimit = length.limit() + length.value()")
                    .addStatement("checkLimit(newLimit, maxLimit())")
                    .addStatement("buffer().putBytes(length.limit(), srcBuffer, srcOffset, srcLength)")
                    .addStatement("limit(newLimit)")
                    .addStatement("valueSet = true")
                    .addStatement("return this")
                    .build();
        }

        private MethodSpec setStringMethod()
        {
            return methodBuilder("set")
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .returns(classType)
                    .addParameter(String.class, "value")
                    .addParameter(Charset.class, "charset")
                    .beginControlFlow("if (value == null)")
                    .addStatement("limit(lengthRW.set(-1).build().limit())")
                    .nextControlFlow("else")
                    .addStatement("byte[] charBytes = value.getBytes(charset)")
                    .addStatement("checkLength(charBytes.length)")
                    .addStatement("$T length = lengthRW.set(charBytes.length).build()", varintType)
                    .addStatement("int newLimit = length.limit() + length.value()")
                    .addStatement("checkLimit(newLimit, maxLimit())")
                    .addStatement("buffer().putBytes(length.limit(), charBytes)")
                    .addStatement("limit(newLimit)")
                    .endControlFlow()
                    .addStatement("valueSet = true")
                    .addStatement("return this")
                    .build();
        }

        private MethodSpec checkLengthMethod()
        {
            return methodBuilder("checkLength")
                    .addModifiers(PRIVATE, STATIC)
                    .addParameter(int.class, "length")
                    .addStatement("final int maxLength = $T.MAX_VALUE - 1", Integer.class)
                    .beginControlFlow("if (length > maxLength)")
                    .addStatement(
                        "final String msg = String.format(\"length=%d is beyond maximum length=%d\", length, maxLength)")
                    .addStatement("throw new IllegalArgumentException(msg)")
                    .endControlFlow()
                    .build();
        }

        private MethodSpec buildMethod()
        {
            return methodBuilder("build")
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .returns(varStringType)
                    .beginControlFlow("if (!valueSet)")
                    .addStatement("set(null, $T.UTF_8)", StandardCharsets.class)
                    .endControlFlow()
                    .addStatement("return super.build()")
                    .build();
        }
    }
}
