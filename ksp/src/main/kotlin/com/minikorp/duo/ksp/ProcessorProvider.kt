package com.minikorp.duo.ksp

import com.google.devtools.ksp.processing.*

private lateinit var _generatorOptions: Map<String, String>
private lateinit var _codeGenerator: CodeGenerator
private lateinit var _logger: KSPLogger

val generatorOptions get() = _generatorOptions
val codeGenerator get() = _codeGenerator
val logger get() = _logger

class ProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        _generatorOptions = environment.options
        _codeGenerator = environment.codeGenerator
        _logger = environment.logger

        return Processor()
    }
}