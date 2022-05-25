package com.minikorp.duo.ksp.generators.drill.adapters

import com.minikorp.duo.ksp.generators.drill.DrillPropertyModel
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeSpec

/**
 * Plain reference that just makes val -> var
 */
class NoOpPropertyAdapter(sourceProp: DrillPropertyModel) : AbstractPropertyAdapter(sourceProp) {

    override val freezeExpression: CodeBlock
        get() = CodeBlock.of(refPropertyAccessor)

    override fun generate(builder: TypeSpec.Builder) {

    }
}