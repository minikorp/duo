package com.minikorp.duo.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.minikorp.duo.ksp.generators.drill.DrillGenerator
import com.minikorp.duo.ksp.generators.state.StateClassGenerator
import com.minikorp.duo.ksp.generators.handler.TypedHandlerGenerator

private lateinit var lateResolver: Resolver
val resolver: Resolver get() = lateResolver

class Processor : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        lateResolver = resolver
        try {
            val out = StateClassGenerator.resolveGenerators() +
                    TypedHandlerGenerator.resolveGenerators()
            out.forEach { it.buildModel() }
            out.forEach { it.emit() }

            DrillGenerator.generate()
        } catch (compilationException: CompilationException) {
            logger.error(compilationException.message, compilationException.node)
        }

        flushDebugPrint()
        return emptyList()
    }

    override fun finish() {
    }
}
