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
import static io.aklivity.zilla.build.maven.plugins.flyweight.internal.generate.TypeNames.DIRECT_BUFFER_TYPE;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;

public final class BoundedOctetsFlyweightGenerator extends ClassSpecGenerator
{
    private final TypeSpec.Builder classBuilder;
    private final ClassName visitorRawType;
    private final BuilderClassBuilder builderClassBuilder;

    public BoundedOctetsFlyweightGenerator(
        ClassName flyweightType)
    {
        super(flyweightType.peerClass("BoundedOctetsFW"));
        this.visitorRawType = flyweightType.nestedClass("Visitor");
        this.classBuilder = classBuilder(thisName)
            .superclass(flyweightType)
            .addModifiers(PUBLIC, ABSTRACT);

        this.builderClassBuilder = new BuilderClassBuilder(thisName, flyweightType);
    }

    @Override
    public TypeSpec generate()
    {
        return classBuilder
            .addMethod(valueMethod())
            .addMethod(getMethod())
            .addMethod(lengthMethod())
            .addType(builderClassBuilder.build())
            .build();
    }

    private MethodSpec valueMethod()
    {
        return methodBuilder("value")
            .addModifiers(PUBLIC, ABSTRACT)
            .returns(DIRECT_BUFFER_TYPE)
            .build();
    }

    private MethodSpec getMethod()
    {
        TypeVariableName typeVarT = TypeVariableName.get("T");
        TypeName visitorType = ParameterizedTypeName.get(visitorRawType, typeVarT);
        return methodBuilder("get")
            .addModifiers(PUBLIC, ABSTRACT)
            .returns(typeVarT)
            .addTypeVariable(typeVarT)
            .addParameter(visitorType, "visitor")
            .build();
    }

    private MethodSpec lengthMethod()
    {
        return methodBuilder("length")
            .addModifiers(PUBLIC, ABSTRACT)
            .returns(int.class)
            .build();
    }

    private static final class BuilderClassBuilder
    {
        private final TypeSpec.Builder classBuilder;
        private final TypeVariableName typeVarT;
        private final TypeName parameterizedBuilderType;
        private final ClassName boundedOctetsType;

        private BuilderClassBuilder(
            ClassName boundedOctetsType,
            ClassName flyweightType)
        {
            ClassName builderRawType = boundedOctetsType.nestedClass("Builder");
            ClassName flyweightBuilderType = flyweightType.nestedClass("Builder");
            this.typeVarT = TypeVariableName.get("T", boundedOctetsType);
            this.parameterizedBuilderType = ParameterizedTypeName.get(builderRawType, typeVarT);
            this.boundedOctetsType = boundedOctetsType;
            this.classBuilder = classBuilder(builderRawType.simpleName())
                .addModifiers(PUBLIC, ABSTRACT, STATIC)
                .superclass(ParameterizedTypeName.get(flyweightBuilderType, typeVarT))
                .addTypeVariable(typeVarT);
        }

        public TypeSpec build()
        {
            return classBuilder
                .addMethod(constructor())
                .addMethod(setWithFlyweight())
                .addMethod(setWithBuffer())
                .addMethod(setWithByteArray())
                .build();
        }

        private MethodSpec constructor()
        {
            return constructorBuilder()
                .addModifiers(PUBLIC)
                .addParameter(typeVarT, "flyweight")
                .addStatement("super(flyweight)")
                .build();
        }

        private MethodSpec setWithFlyweight()
        {
            return methodBuilder("set")
                .addModifiers(PUBLIC, ABSTRACT)
                .returns(parameterizedBuilderType)
                .addParameter(boundedOctetsType, "value")
                .build();
        }

        private MethodSpec setWithBuffer()
        {
            return methodBuilder("set")
                .addModifiers(PUBLIC, ABSTRACT)
                .returns(parameterizedBuilderType)
                .addParameter(DIRECT_BUFFER_TYPE, "value")
                .addParameter(int.class, "offset")
                .addParameter(int.class, "length")
                .build();
        }

        private MethodSpec setWithByteArray()
        {
            return methodBuilder("set")
                .addModifiers(PUBLIC, ABSTRACT)
                .returns(parameterizedBuilderType)
                .addParameter(byte[].class, "value")
                .build();
        }
    }
}
