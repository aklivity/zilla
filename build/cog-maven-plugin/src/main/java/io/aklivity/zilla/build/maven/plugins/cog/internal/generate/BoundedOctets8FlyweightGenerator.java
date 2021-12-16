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
import static io.aklivity.zilla.build.maven.plugins.cog.internal.generate.TypeNames.BIT_UTIL_TYPE;
import static io.aklivity.zilla.build.maven.plugins.cog.internal.generate.TypeNames.DIRECT_BUFFER_TYPE;
import static io.aklivity.zilla.build.maven.plugins.cog.internal.generate.TypeNames.MUTABLE_DIRECT_BUFFER_TYPE;
import static io.aklivity.zilla.build.maven.plugins.cog.internal.generate.TypeNames.UNSAFE_BUFFER_TYPE;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;

public final class BoundedOctets8FlyweightGenerator extends ClassSpecGenerator
{
    private final TypeSpec.Builder classBuilder;
    private final ClassName visitorClass;
    private final BuilderClassBuilder builderClassBuilder;

    public BoundedOctets8FlyweightGenerator(
        ClassName flyweightType,
        ClassName boundedOctetsType)
    {
        super(flyweightType.peerClass("BoundedOctets8FW"));
        this.visitorClass = flyweightType.nestedClass("Visitor");
        this.classBuilder = classBuilder(thisName)
            .superclass(boundedOctetsType)
            .addModifiers(PUBLIC, FINAL);

        this.builderClassBuilder = new BuilderClassBuilder(thisName, boundedOctetsType);
    }

    @Override
    public TypeSpec generate()
    {
        return classBuilder
            .addField(lengthSizeConstant())
            .addField(lengthOffsetConstant())
            .addField(valueOffsetConstant())
            .addField(valueField())
            .addMethod(getMethod())
            .addMethod(valueMethod())
            .addMethod(lengthMethod())
            .addMethod(tryWrapMethod())
            .addMethod(wrapMethod())
            .addMethod(limitMethod())
            .addMethod(toStringMethod())
            .addType(builderClassBuilder.build())
            .build();
    }

    private FieldSpec lengthSizeConstant()
    {
        return FieldSpec.builder(int.class, "LENGTH_SIZE", PRIVATE, STATIC, FINAL)
            .initializer("$T.SIZE_OF_BYTE", BIT_UTIL_TYPE)
            .build();
    }

    private FieldSpec lengthOffsetConstant()
    {
        return FieldSpec.builder(int.class, "LENGTH_OFFSET", PRIVATE, STATIC, FINAL)
            .initializer("0")
            .build();
    }

    private FieldSpec valueOffsetConstant()
    {
        return FieldSpec.builder(int.class, "VALUE_OFFSET", PRIVATE, STATIC, FINAL)
            .initializer("LENGTH_OFFSET + LENGTH_SIZE")
            .build();
    }

    private FieldSpec valueField()
    {
        return FieldSpec.builder(DIRECT_BUFFER_TYPE, "valueRO", PRIVATE, FINAL)
            .initializer("new $T(0L, 0)", UNSAFE_BUFFER_TYPE)
            .build();
    }

    private MethodSpec getMethod()
    {
        TypeVariableName typeVarT = TypeVariableName.get("T");
        return methodBuilder("get")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .addTypeVariable(typeVarT)
            .returns(typeVarT)
            .addParameter(ParameterizedTypeName.get(visitorClass, typeVarT), "visitor")
            .addStatement("return visitor.visit(buffer(), offset() + VALUE_OFFSET, limit())")
            .build();
    }

    private MethodSpec valueMethod()
    {
        return methodBuilder("value")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .returns(DIRECT_BUFFER_TYPE)
            .addStatement("return valueRO")
            .build();
    }

    private MethodSpec lengthMethod()
    {
        return methodBuilder("length")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .returns(int.class)
            .addStatement("return buffer().getByte(offset() + LENGTH_OFFSET) & 0xFF")
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
            .beginControlFlow("if (super.tryWrap(buffer, offset, maxLimit) == null)")
            .addStatement("return null")
            .endControlFlow()
            .addStatement("valueRO.wrap(buffer, offset + VALUE_OFFSET, length())")
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
            .addStatement("valueRO.wrap(buffer, offset + VALUE_OFFSET, length())")
            .addStatement("checkLimit(limit(), maxLimit)")
            .addStatement("return this")
            .build();
    }

    private MethodSpec limitMethod()
    {
        return methodBuilder("limit")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .returns(int.class)
            .addStatement("return offset() + LENGTH_SIZE + length()")
            .build();
    }

    private MethodSpec toStringMethod()
    {
        return methodBuilder("toString")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .returns(String.class)
            .addStatement("return String.format(\"boundedOctets8[%d]\", length())")
            .build();
    }

    private static final class BuilderClassBuilder
    {
        private final TypeSpec.Builder classBuilder;
        private final ClassName boundedOctets8BuilderType;
        private final ClassName boundedOctetsType;

        private BuilderClassBuilder(
            ClassName boundedOctets8Type,
            ClassName boundedOctetsType)
        {
            this.boundedOctets8BuilderType = boundedOctets8Type.nestedClass("Builder");
            this.boundedOctetsType = boundedOctetsType;
            ClassName boundedOctetsBuilderType = boundedOctetsType.nestedClass("Builder");
            this.classBuilder = classBuilder(boundedOctets8BuilderType.simpleName())
                .addModifiers(PUBLIC, STATIC, FINAL)
                .superclass(ParameterizedTypeName.get(boundedOctetsBuilderType, boundedOctets8Type));
        }

        public TypeSpec build()
        {
            return classBuilder
                .addMethod(constructor())
                .addMethod(setWithFlyweight())
                .addMethod(setWithBuffer())
                .addMethod(setWithByteArray())
                .addMethod(wrapMethod())
                .build();
        }

        private MethodSpec constructor()
        {
            return constructorBuilder()
                .addModifiers(PUBLIC)
                .addStatement("super(new BoundedOctets8FW())")
                .build();
        }

        private MethodSpec setWithFlyweight()
        {
            return methodBuilder("set")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(boundedOctets8BuilderType)
                .addParameter(boundedOctetsType, "value")
                .addStatement("int newLimit = offset() + LENGTH_SIZE + value.length()")
                .addStatement("checkLimit(newLimit, maxLimit())")
                .addStatement("buffer().putByte(offset() + LENGTH_OFFSET, (byte) (value.length() & 0xFF))")
                .addStatement("buffer().putBytes(offset() + VALUE_OFFSET, value.buffer(), value.offset() + VALUE_OFFSET, value" +
                    ".length())")
                .addStatement("limit(newLimit)")
                .addStatement("return this")
                .build();
        }

        private MethodSpec setWithBuffer()
        {
            return methodBuilder("set")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(boundedOctets8BuilderType)
                .addParameter(DIRECT_BUFFER_TYPE, "value")
                .addParameter(int.class, "offset")
                .addParameter(int.class, "length")
                .addStatement("int newLimit = offset() + LENGTH_SIZE + length")
                .addStatement("checkLimit(newLimit, maxLimit())")
                .addStatement("buffer().putByte(offset() + LENGTH_OFFSET, (byte) (length & 0xFF))")
                .addStatement("buffer().putBytes(offset() + VALUE_OFFSET, value, offset, length)")
                .addStatement("limit(newLimit)")
                .addStatement("return this")
                .build();
        }

        private MethodSpec setWithByteArray()
        {
            return methodBuilder("set")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(boundedOctets8BuilderType)
                .addParameter(byte[].class, "value")
                .addStatement("int newLimit = offset() + LENGTH_SIZE + value.length")
                .addStatement("checkLimit(newLimit, maxLimit())")
                .addStatement("buffer().putByte(offset() + LENGTH_OFFSET, (byte) (value.length & 0xFF))")
                .addStatement("buffer().putBytes(offset() + VALUE_OFFSET, value)")
                .addStatement("limit(newLimit)")
                .addStatement("return this")
                .build();
        }

        private MethodSpec wrapMethod()
        {
            return methodBuilder("wrap")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(boundedOctets8BuilderType)
                .addParameter(MUTABLE_DIRECT_BUFFER_TYPE, "buffer")
                .addParameter(int.class, "offset")
                .addParameter(int.class, "maxLimit")
                .addStatement("checkLimit(offset + LENGTH_SIZE, maxLimit)")
                .addStatement("super.wrap(buffer, offset, maxLimit)")
                .addStatement("return this")
                .build();
        }
    }
}
