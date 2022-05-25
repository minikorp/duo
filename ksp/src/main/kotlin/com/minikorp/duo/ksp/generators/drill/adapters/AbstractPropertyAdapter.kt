package com.minikorp.duo.ksp.generators.drill.adapters

import com.minikorp.drill.UNSET_VALUE
import com.minikorp.duo.ksp.generators.drill.DrillClassModel
import com.minikorp.duo.ksp.generators.drill.DrillPropertyModel
import com.squareup.kotlinpoet.*

abstract class AbstractPropertyAdapter(val sourceProp: DrillPropertyModel) {
    companion object {
        val unsetClassName = UNSET_VALUE::class.asClassName()

        fun createAdapter(sourceProp: DrillPropertyModel): AbstractPropertyAdapter {
            val type = sourceProp.typeName

            return when {
                sourceProp.ignore -> {
                    NoOpPropertyAdapter(sourceProp)
                }
                sourceProp.asReference -> {
                    SimplePropertyAdapter(sourceProp)
                }
                ReferencePropertyAdapter.supportsType(type) -> {
                    ReferencePropertyAdapter(sourceProp)
                }
                CollectionPropertyAdapter.supportsType(type) -> {
                    CollectionPropertyAdapter(sourceProp)
                }
                else -> SimplePropertyAdapter(sourceProp)
            }
        }
    }

    /**
     * Block of code to transform from a mutable type back to the immutable type
     */
    abstract val freezeExpression: CodeBlock

    /**
     * Expression to check if the property is dirty
     */
    open val isDirtyExpression: CodeBlock
        get() {
            val dirtyIf = if (nullable) {
                "(${sourceProp.name}${call}dirty() ?: true)"
            } else {
                "${sourceProp.name}${call}dirty()"
            }
            return CodeBlock.of(
                "($backingPropertyName !== %T && $backingPropertyName !== $refPropertyAccessor) || $dirtyIf",
                unsetClassName
            )
        }

    open val stringExpression: String
        get() {
            return "\${${backingPropertyName}}"
        }

    abstract fun generate(builder: TypeSpec.Builder)

    /** parent.real_prop */
    val refPropertyAccessor = "${DrillClassModel.SOURCE_PROPERTY}.${sourceProp.name}"
    val refPropertyKdoc =
        CodeBlock.of("[%T.${sourceProp.name}]\n${this.javaClass.simpleName}", sourceProp.container.originalClassName)
    val backingPropertyName = "_${sourceProp.name}"
    val nullable: Boolean = sourceProp.type.isMarkedNullable

    /** Safe call ?. or . */
    val call = if (nullable) "?." else "."

    /** Backing field that stores the mutable object or special [UNSET_VALUE] token. */
    val backingField = PropertySpec.builder(
        backingPropertyName,
        ANY.copy(nullable = true)
    ).addModifiers(KModifier.PRIVATE)
        .mutable(true)
        .initializer(
            "%T",
            unsetClassName
        )
        .build()


}