package ru.hollowhorizon.hollowengine.common.scripting.index

import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.ReceiverParameterDescriptor
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.descriptors.impl.DeclarationDescriptorVisitorEmptyBodies
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

object ExtractSymbolExtensionReceiverType :
    DeclarationDescriptorVisitorEmptyBodies<FqName?, Unit>() {

    private fun convert(desc: ReceiverParameterDescriptor): FqName? = desc.value.type.constructor.declarationDescriptor?.fqNameSafe

    override fun visitFunctionDescriptor(descriptor: FunctionDescriptor, data: Unit?): FqName? {
        return descriptor.extensionReceiverParameter?.let(this::convert)
    }

    override fun visitVariableDescriptor(desc: VariableDescriptor, data: Unit?) = desc.extensionReceiverParameter?.let(this::convert)
}