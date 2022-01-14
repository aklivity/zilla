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
import static io.aklivity.zilla.build.maven.plugins.flyweight.internal.generate.TypeNames.DIRECT_BUFFER_TYPE;
import static io.aklivity.zilla.build.maven.plugins.flyweight.internal.generate.TypeNames.MUTABLE_DIRECT_BUFFER_TYPE;
import static io.aklivity.zilla.build.maven.plugins.flyweight.internal.generate.TypeNames.UNSAFE_BUFFER_TYPE;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;

public final class OctetsFlyweightGenerator extends ClassSpecGenerator
{
    private final ClassName visitorRawType;
    private final TypeSpec.Builder classBuilder;
    private final BuilderClassBuilder builderClassBuilder;

    public OctetsFlyweightGenerator(
        ClassName flyweightType)
    {
        super(flyweightType.peerClass("OctetsFW"));

        this.visitorRawType = flyweightType.nestedClass("Visitor");
        this.classBuilder = classBuilder(thisName).superclass(flyweightType).addModifiers(PUBLIC, FINAL);
        this.builderClassBuilder = new BuilderClassBuilder(thisName, flyweightType);
    }

    @Override
    public TypeSpec generate()
    {
        return classBuilder
                .addField(valueField())
                .addMethod(getMethod())
                .addMethod(valueMethod())
                .addMethod(limitMethod())
                .addMethod(tryWrapMethod())
                .addMethod(wrapMethod())
                .addMethod(toStringMethod())
                .addType(builderClassBuilder.build())
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
        TypeName visitorType = ParameterizedTypeName.get(visitorRawType, typeVarT);

        return methodBuilder("get")
                .addModifiers(PUBLIC)
                .addTypeVariable(typeVarT)
                .addParameter(visitorType, "visitor")
                .returns(typeVarT)
                .addStatement("DirectBuffer buffer = buffer()")
                .addStatement("int offset = offset()")
                .addStatement("int limit = limit()")
                .addStatement("return visitor.visit(buffer, offset, limit)")
                .build();
    }

    private MethodSpec valueMethod()
    {
        return methodBuilder("value")
                .addModifiers(PUBLIC)
                .returns(DIRECT_BUFFER_TYPE)
                .addStatement("return valueRO")
                .build();
    }

    private MethodSpec limitMethod()
    {
        return methodBuilder("limit")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(int.class)
                .addStatement("return maxLimit()")
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
                .beginControlFlow("if (null == super.tryWrap(buffer, offset, maxLimit))")
                .addStatement("return null")
                .endControlFlow()
                .addStatement("final int sizeof = sizeof()")
                .addStatement("valueRO.wrap(buffer, sizeof != 0 ? offset : 0, sizeof)")
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
                .addStatement("final int sizeof = sizeof()")
                .addStatement("valueRO.wrap(buffer, sizeof != 0 ? offset : 0, sizeof)")
                .addStatement("return this")
                .build();
    }

    private MethodSpec toStringMethod()
    {
        return methodBuilder("toString")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(String.class)
                .addStatement("return String.format(\"octets[%d]\", sizeof())")
                .build();
    }

    private static final class BuilderClassBuilder
    {
        private final TypeSpec.Builder classBuilder;
        private final ClassName classType;
        private final ClassName octetsType;
        private final ClassName visitorType;

        private BuilderClassBuilder(
            ClassName octetsType,
            ClassName flyweightType)
        {
            ClassName builderRawType = flyweightType.nestedClass("Builder");
            TypeName builderType = ParameterizedTypeName.get(builderRawType, octetsType);

            this.octetsType = octetsType;
            this.classType = octetsType.nestedClass("Builder");
            this.visitorType = builderRawType.nestedClass("Visitor");
            this.classBuilder = classBuilder(classType.simpleName())
                    .addModifiers(PUBLIC, STATIC, FINAL)
                    .superclass(builderType);
        }

        public TypeSpec build()
        {
            return classBuilder.addMethod(constructor())
                    .addMethod(wrapMethod())
                    .addMethod(resetMethod())
                    .addMethod(setMethod())
                    .addMethod(setMethodViaBuffer())
                    .addMethod(setMethodViaByteArray())
                    .addMethod(setMethodViaMutator())
                    .addMethod(putMethod())
                    .addMethod(putMethodViaBuffer())
                    .addMethod(putMethodViaByteArray())
                    .addMethod(putMethodViaMutator())
                    .build();
        }

        private MethodSpec constructor()
        {
            return constructorBuilder()
                    .addModifiers(PUBLIC)
                    .addStatement("super(new $T())", octetsType)
                    .build();
        }

        private MethodSpec wrapMethod()
        {
            return methodBuilder("wrap")
                    .addModifiers(PUBLIC)
                    .returns(octetsType.nestedClass("Builder"))
                    .addParameter(MUTABLE_DIRECT_BUFFER_TYPE, "buffer")
                    .addParameter(int.class, "offset")
                    .addParameter(int.class, "maxLimit")
                    .addStatement("super.wrap(buffer, offset, maxLimit)")
                    .addStatement("return this")
                    .build();
        }

        private MethodSpec resetMethod()
        {
            return methodBuilder("reset")
                    .addAnnotation(AnnotationSpec.builder(Deprecated.class).build())
                    .addModifiers(PUBLIC)
                    .returns(octetsType.nestedClass("Builder"))
                    .addStatement("limit(offset())")
                    .addStatement("return this")
                    .build();
        }

        private MethodSpec setMethod()
        {
            return methodBuilder("set")
                    .addModifiers(PUBLIC)
                    .returns(octetsType.nestedClass("Builder"))
                    .addParameter(octetsType, "value")
                    .addStatement("int newLimit = offset() + value.sizeof()")
                    .addStatement("checkLimit(newLimit, maxLimit())")
                    .addStatement("buffer().putBytes(offset(), value.buffer(), value.offset(), value.sizeof())")
                    .addStatement("limit(newLimit)")
                    .addStatement("return this")
                    .build();
        }

        private MethodSpec setMethodViaBuffer()
        {
            return methodBuilder("set")
                    .addModifiers(PUBLIC)
                    .returns(octetsType.nestedClass("Builder"))
                    .addParameter(DIRECT_BUFFER_TYPE, "value")
                    .addParameter(int.class, "offset")
                    .addParameter(int.class, "length")
                    .addStatement("int newLimit = offset() + length")
                    .addStatement("checkLimit(newLimit, maxLimit())")
                    .addStatement("buffer().putBytes(offset(), value, offset, length)")
                    .addStatement("limit(newLimit)")
                    .addStatement("return this")
                    .build();
        }

        private MethodSpec setMethodViaByteArray()
        {
            return methodBuilder("set")
                    .addModifiers(PUBLIC)
                    .returns(octetsType.nestedClass("Builder"))
                    .addParameter(byte[].class, "value")
                    .addStatement("int newLimit = offset() + value.length")
                    .addStatement("checkLimit(newLimit, maxLimit())")
                    .addStatement("buffer().putBytes(offset(), value)")
                    .addStatement("limit(newLimit)")
                    .addStatement("return this")
                    .build();
        }

        private MethodSpec setMethodViaMutator()
        {
            return methodBuilder("set")
                    .addModifiers(PUBLIC)
                    .returns(octetsType.nestedClass("Builder"))
                    .addParameter(visitorType, "visitor")
                    .addStatement("int length = visitor.visit(buffer(), offset(), maxLimit())")
                    .addStatement("checkLimit(offset() + length, maxLimit())")
                    .addStatement("limit(offset() + length)")
                    .addStatement("return this")
                    .build();
        }

        private MethodSpec putMethodViaMutator()
        {
            return methodBuilder("put")
                    .addModifiers(PUBLIC)
                    .returns(octetsType.nestedClass("Builder"))
                    .addParameter(visitorType, "visitor")
                    .addStatement("int length = visitor.visit(buffer(), limit(), maxLimit())")
                    .addStatement("checkLimit(limit() + length, maxLimit())")
                    .addStatement("limit(limit() + length)")
                    .addStatement("return this")
                    .build();
        }

        private MethodSpec putMethod()
        {
            return methodBuilder("put")
                    .addModifiers(PUBLIC)
                    .returns(octetsType.nestedClass("Builder"))
                    .addParameter(octetsType, "value")
                    .addStatement("int newLimit = limit() + value.sizeof()")
                    .addStatement("checkLimit(newLimit, maxLimit())")
                    .addStatement("buffer().putBytes(limit(), value.buffer(), value.offset(), value.sizeof())")
                    .addStatement("limit(newLimit)")
                    .addStatement("return this")
                    .build();
        }

        private MethodSpec putMethodViaBuffer()
        {
            return methodBuilder("put")
                    .addModifiers(PUBLIC)
                    .returns(octetsType.nestedClass("Builder"))
                    .addParameter(DIRECT_BUFFER_TYPE, "value")
                    .addParameter(int.class, "offset")
                    .addParameter(int.class, "length")
                    .addStatement("int newLimit = limit() + length")
                    .addStatement("checkLimit(newLimit, maxLimit())")
                    .addStatement("buffer().putBytes(limit(), value, offset, length)")
                    .addStatement("limit(newLimit)")
                    .addStatement("return this")
                    .build();
        }

        private MethodSpec putMethodViaByteArray()
        {
            return methodBuilder("put")
                    .addModifiers(PUBLIC)
                    .returns(octetsType.nestedClass("Builder"))
                    .addParameter(byte[].class, "value")
                    .addStatement("int newLimit = limit() + value.length")
                    .addStatement("checkLimit(newLimit, maxLimit())")
                    .addStatement("buffer().putBytes(limit(), value)")
                    .addStatement("limit(newLimit)")
                    .addStatement("return this")
                    .build();
        }
    }
}
