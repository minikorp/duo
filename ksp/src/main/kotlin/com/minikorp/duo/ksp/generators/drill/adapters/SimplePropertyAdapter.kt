package com.minikorp.duo.ksp.generators.drill.adapters

import com.minikorp.duo.ksp.generators.drill.DrillClassModel
import com.minikorp.duo.ksp.generators.drill.DrillPropertyModel
import com.squareup.kotlinpoet.*

/**
 * Plain reference that just makes val -> var
 */
class SimplePropertyAdapter(sourceProp: DrillPropertyModel) : AbstractPropertyAdapter(sourceProp) {

    override val freezeExpression: CodeBlock
        get() = CodeBlock.of(sourceProp.name)

    override val isDirtyExpression: CodeBlock
        get() = CodeBlock.of("$refPropertyAccessor !== ${sourceProp.name}")

    override val stringExpression: String
        get() {
            val q = if (sourceProp.typeName == STRING) "\\\"" else ""
            return "$q\${$refPropertyAccessor}$q·->·$q\${${sourceProp.name}}$q"
        }

    override fun generate(builder: TypeSpec.Builder) {
        builder.addProperty(
            PropertySpec.builder(
                sourceProp.name,
                sourceProp.typeName
            ).mutable(true)
                .addKdoc(refPropertyKdoc)
                .initializer("${DrillClassModel.SOURCE_PROPERTY}.${sourceProp.name}")
                .setter(
                    FunSpec.setterBuilder()
                        .addParameter("value", sourceProp.typeName)
                        .addCode("field = value.also { markDirty() }")
                        .build()
                ).build()
        )
    }
}