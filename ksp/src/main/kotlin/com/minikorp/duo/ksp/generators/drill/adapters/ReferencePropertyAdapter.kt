package com.minikorp.duo.ksp.generators.drill.adapters

import com.minikorp.duo.ksp.generators.drill.DrillClassGenerator
import com.minikorp.duo.ksp.generators.drill.DrillPropertyModel
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview
import com.squareup.kotlinpoet.ksp.toTypeName

@OptIn(KotlinPoetKspPreview::class)
class ReferencePropertyAdapter(sourceProp: DrillPropertyModel) : AbstractPropertyAdapter(sourceProp) {

    companion object {
        fun supportsType(type: TypeName): Boolean {
            return DrillClassGenerator.findMutableClass(type) != null
        }
    }

    private val mutableClassTypeName = kotlin.run {
        val nestedMutable = DrillClassGenerator.findMutableClass(sourceProp.type.toTypeName())
        nestedMutable!!.mutableClassName.copy(nullable = sourceProp.type.isMarkedNullable)
    }

    override val freezeExpression =
        CodeBlock.of(
            "if ($backingPropertyName === %T) " +
                    "$refPropertyAccessor " +
                    "else ($backingPropertyName as %T)${call}freeze()",
            unsetClassName,
            mutableClassTypeName
        )

    override fun generate(builder: TypeSpec.Builder) {
        val accessorField =
            PropertySpec.builder(sourceProp.name, mutableClassTypeName)
                .addKdoc(refPropertyKdoc)
                .mutable(false)
                .getter(
                    FunSpec.getterBuilder()
                        .beginControlFlow(
                            "if (${backingPropertyName} === %T)",
                            unsetClassName
                        )
                        .addStatement("$backingPropertyName = ${refPropertyAccessor}${call}toMutable(this)")
                        .endControlFlow()
                        .addStatement("return $backingPropertyName as %T", mutableClassTypeName)
                        .build()
                )
                .build()

        val setterFunction = FunSpec.builder("set" + sourceProp.name.capitalize())
            .addParameter(sourceProp.name, sourceProp.typeName)
            .addStatement("$backingPropertyName = ${sourceProp.name}${call}toMutable(this)")
            .addStatement("markDirty()")
            .build()

        //Public mutable class accessor
        builder.addFunction(setterFunction)
        builder.addProperties(listOf(backingField, accessorField))
    }
}