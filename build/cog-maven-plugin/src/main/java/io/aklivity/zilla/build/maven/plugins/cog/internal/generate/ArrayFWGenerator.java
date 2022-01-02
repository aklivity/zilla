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
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static io.aklivity.zilla.build.maven.plugins.cog.internal.generate.TypeNames.DIRECT_BUFFER_TYPE;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import java.util.function.Consumer;
import java.util.function.Predicate;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import com.squareup.javapoet.WildcardTypeName;

public final class ArrayFWGenerator extends ClassSpecGenerator
{
    private final TypeSpec.Builder classBuilder;
    private final BuilderClassBuilder builderClassBuilder;
    private final TypeVariableName typeVarV;

    public ArrayFWGenerator(
        ClassName flyweightType)
    {
        super(flyweightType.peerClass("ArrayFW"));
        this.typeVarV = TypeVariableName.get("V", flyweightType);
        this.classBuilder = classBuilder(thisName)
            .superclass(flyweightType)
            .addModifiers(PUBLIC, ABSTRACT)
            .addTypeVariable(typeVarV);
        this.builderClassBuilder = new BuilderClassBuilder(thisName, flyweightType);
    }

    @Override
    public TypeSpec generate()
    {
        return classBuilder
            .addMethod(lengthMethod())
            .addMethod(fieldCountMethod())
            .addMethod(fieldsOffsetMethod())
            .addMethod(maxLengthMethod())
            .addMethod(forEachMethod())
            .addMethod(anyMatchMethod())
            .addMethod(matchFirstMethod())
            .addMethod(isEmptyMethod())
            .addMethod(itemsMethod())
            .addMethod(maxLengthMutatorMethod())
            .addType(builderClassBuilder.build())
            .build();
    }

    private MethodSpec lengthMethod()
    {
        return methodBuilder("length")
            .addModifiers(PUBLIC, ABSTRACT)
            .returns(int.class)
            .build();
    }

    private MethodSpec fieldCountMethod()
    {
        return methodBuilder("fieldCount")
            .addModifiers(PUBLIC, ABSTRACT)
            .returns(int.class)
            .build();
    }

    private MethodSpec fieldsOffsetMethod()
    {
        return methodBuilder("fieldsOffset")
            .addModifiers(PUBLIC, ABSTRACT)
            .returns(int.class)
            .build();
    }

    private MethodSpec maxLengthMethod()
    {
        return methodBuilder("maxLength")
            .addModifiers(PUBLIC, ABSTRACT)
            .returns(int.class)
            .build();
    }

    private MethodSpec forEachMethod()
    {
        TypeName itemType = WildcardTypeName.supertypeOf(typeVarV);
        TypeName consumerType = ParameterizedTypeName.get(ClassName.get(Consumer.class), itemType);
        return methodBuilder("forEach")
            .addModifiers(PUBLIC, ABSTRACT)
            .returns(void.class)
            .addParameter(consumerType, "consumer")
            .build();
    }

    private MethodSpec anyMatchMethod()
    {
        TypeName itemType = WildcardTypeName.supertypeOf(typeVarV);
        TypeName predicateType = ParameterizedTypeName.get(ClassName.get(Predicate.class), itemType);
        return methodBuilder("anyMatch")
            .addModifiers(PUBLIC, ABSTRACT)
            .returns(boolean.class)
            .addParameter(predicateType, "predicate")
            .build();
    }

    private MethodSpec matchFirstMethod()
    {
        TypeName itemType = WildcardTypeName.supertypeOf(typeVarV);
        TypeName predicateType = ParameterizedTypeName.get(ClassName.get(Predicate.class), itemType);
        return methodBuilder("matchFirst")
            .addModifiers(PUBLIC, ABSTRACT)
            .returns(typeVarV)
            .addParameter(predicateType, "predicate")
            .build();
    }

    private MethodSpec isEmptyMethod()
    {
        return methodBuilder("isEmpty")
            .addModifiers(PUBLIC, ABSTRACT)
            .returns(boolean.class)
            .build();
    }

    private MethodSpec itemsMethod()
    {
        return methodBuilder("items")
            .addModifiers(PUBLIC, ABSTRACT)
            .returns(DIRECT_BUFFER_TYPE)
            .build();
    }

    private MethodSpec maxLengthMutatorMethod()
    {
        return methodBuilder("maxLength")
            .addModifiers(PUBLIC, ABSTRACT)
            .returns(void.class)
            .addParameter(int.class, "maxLength")
            .build();
    }

    private static final class BuilderClassBuilder
    {
        private final TypeSpec.Builder classBuilder;
        private final TypeVariableName typeVarB;
        private final TypeVariableName typeVarT;
        private final TypeName parameterizedBuilderType;

        private BuilderClassBuilder(
            ClassName arrayType,
            ClassName flyweightType)
        {
            TypeVariableName typeVarV = TypeVariableName.get("V", flyweightType);
            this.typeVarT = TypeVariableName.get("T", ParameterizedTypeName.get(arrayType, typeVarV));
            ClassName flyweightBuilderType = flyweightType.nestedClass("Builder");
            ClassName arrayBuilderType = arrayType.nestedClass("Builder");
            this.typeVarB = TypeVariableName.get("B", ParameterizedTypeName.get(flyweightBuilderType, typeVarV));
            this.parameterizedBuilderType = ParameterizedTypeName.get(arrayBuilderType, typeVarT, typeVarB, typeVarV);
            this.classBuilder = classBuilder(arrayBuilderType.simpleName())
                .addModifiers(PUBLIC, ABSTRACT, STATIC)
                .superclass(ParameterizedTypeName.get(flyweightBuilderType, typeVarT))
                .addTypeVariable(typeVarT)
                .addTypeVariable(typeVarB)
                .addTypeVariable(typeVarV);
        }

        public TypeSpec build()
        {
            return classBuilder
                .addMethod(constructor())
                .addMethod(itemMethod())
                .addMethod(itemsMethod())
                .addMethod(fieldsOffsetMethod())
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

        private MethodSpec itemMethod()
        {
            TypeName consumerType = ParameterizedTypeName.get(ClassName.get(Consumer.class), typeVarB);
            return methodBuilder("item")
                .addModifiers(PUBLIC, ABSTRACT)
                .addParameter(consumerType, "consumer")
                .returns(parameterizedBuilderType)
                .build();
        }

        private MethodSpec itemsMethod()
        {
            return methodBuilder("items")
                .addModifiers(PUBLIC, ABSTRACT)
                .returns(parameterizedBuilderType)
                .addParameter(DIRECT_BUFFER_TYPE, "buffer")
                .addParameter(int.class, "srcOffset")
                .addParameter(int.class, "length")
                .addParameter(int.class, "fieldCount")
                .addParameter(int.class, "maxLength")
                .build();
        }

        private MethodSpec fieldsOffsetMethod()
        {
            return methodBuilder("fieldsOffset")
                .addModifiers(PUBLIC, ABSTRACT)
                .returns(int.class)
                .build();
        }
    }
}
