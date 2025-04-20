@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package ru.hollowhorizon.hollowengine.compiler

import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.fir.declarations.builder.buildRegularClass
import org.jetbrains.kotlin.fir.declarations.builder.buildSimpleFunction
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeAliasSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.util.isNullable
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.types.checker.SimpleClassicTypeSystemContext.argumentsCount
import kotlin.metadata.*
import org.jetbrains.kotlin.descriptors.Modality as IrModality

object IrToKmConverter {
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    fun toKm(type: IrClass) = KmClass().apply {
        // Имя класса
        name = type.name.asString()

        // Видимость
        visibility = type.visibility.toKmVisibility()
        modality = type.modality.toKmModality()

        // Модификаторы
        hasAnnotations = type.annotations.isNotEmpty()
        type.companionObject()?.name?.asString()?.let {
            companionObject = it
        }
        isData = type.isData
        isInner = type.isInner
        isValue = type.isValue
        isExternal = type.isExternal
        isExpect = type.isExpect
        isFunInterface = type.isFun
        hasEnumEntries = type.hasEnumEntries


        // Супертипы
        supertypes.addAll(type.superTypes.map { it.toKmType() })

        // Типовые параметры
        typeParameters.addAll(
            type.typeParameters.mapIndexed { i, param ->
                KmTypeParameter(
                    name = param.name.asString(),
                    id = i,
                    variance = param.variance.toKmVariance()
                ).apply {
                    this.isReified = param.isReified
                }
            }
        )

        // Функции и свойства
        type.declarations.forEach { declaration ->
            when (declaration) {
                is IrClass -> {
                    if(declaration.isInner) {
                        nestedClasses.add(declaration.name.asString())
                    }
                }

                is IrConstructor -> {
                    constructors.add(KmConstructor().apply {
                        this.valueParameters.addAll(declaration.valueParameters.map { it.toKmType() })
                        this.visibility = declaration.visibility.toKmVisibility()
                        this.hasAnnotations = declaration.annotations.isNotEmpty()
                        this.isSecondary = !declaration.isPrimary
                    })
                }

                is IrFunction -> {
                    functions.add(
                        KmFunction(
                            name = declaration.name.asString(),
                        ).apply {
                            this.visibility = declaration.visibility.toKmVisibility()
                            this.isInline = declaration.isInline
                            this.returnType = declaration.returnType.toKmType()
                            this.receiverParameterType = declaration.dispatchReceiverParameter?.type?.toKmType()
                            this.valueParameters.addAll(
                                declaration.valueParameters.map {
                                    it.toKmType()
                                }
                            )
                        }
                    )
                }

                is IrProperty -> {
                    properties.add(
                        KmProperty(
                            name = declaration.name.asString(),
                        ).apply {
                            this.visibility = declaration.visibility.toKmVisibility()
                            this.isVar = declaration.isVar
                            this.kind = when {
                                declaration.isFakeOverride -> MemberKind.FAKE_OVERRIDE
                                declaration.isDelegated -> MemberKind.DELEGATION
                                declaration.origin.isSynthetic -> MemberKind.SYNTHESIZED
                                else -> MemberKind.DECLARATION
                            }
                            this.isConst = declaration.isConst
                            this.isLateinit = declaration.isLateinit
                            this.isExternal = declaration.isExternal
                            this.isDelegated = declaration.isDelegated
                            this.isExpect = declaration.isExpect
                            this.returnType = (declaration.backingField?.type ?: declaration.getter?.returnType ?: declaration.setter?.returnType ?: pluginContext.irBuiltIns.anyNType).toKmType()
                        }
                    )
                }
            }
        }

        // Companion object
        companionObject = type.companionObject()?.name?.asString()
    }

    // Конвертер видимости Ir -> Km
    private fun DescriptorVisibility.toKmVisibility(): Visibility = when (this) {
        DescriptorVisibilities.PUBLIC -> Visibility.PUBLIC
        DescriptorVisibilities.PRIVATE -> Visibility.PRIVATE
        DescriptorVisibilities.PROTECTED -> Visibility.PROTECTED
        DescriptorVisibilities.INTERNAL -> Visibility.INTERNAL
        DescriptorVisibilities.LOCAL -> Visibility.LOCAL
        DescriptorVisibilities.PRIVATE_TO_THIS -> Visibility.PRIVATE
        else -> Visibility.PUBLIC
    }

    private fun IrModality.toKmModality(): Modality = when (this) {
        IrModality.OPEN -> Modality.OPEN
        IrModality.FINAL -> Modality.FINAL
        IrModality.ABSTRACT -> Modality.ABSTRACT
        IrModality.SEALED -> Modality.SEALED
        else -> Modality.OPEN
    }

    private fun IrType.toKmType(): KmType {
        if(this !is IrSimpleType) error("Unsupported type $this")
        val classifier: KmClassifier? = when (val sym = this.classifierOrFail) {
            is IrClassSymbol ->
                KmClassifier.Class(sym.owner.fqNameWhenAvailable!!.asString())

            is IrTypeParameterSymbol ->
                KmClassifier.TypeParameter(sym.owner.index)

            is IrTypeAliasSymbol ->
                KmClassifier.TypeAlias(sym.owner.fqNameWhenAvailable!!.asString())

            else ->
                null
        }

        // 3. Рекурсивно конвертим аргументы проекции
        val args: List<KmTypeProjection> = this.arguments.map { proj ->
            when (proj) {
                is IrStarProjection ->
                    KmTypeProjection.STAR

                is IrTypeProjection -> {
                    val kmType = proj.type.toKmType()
                    KmTypeProjection(proj.variance.toKmVariance(), kmType)
                }

                else ->
                    error("Unknown projection kind: $proj")
            }
        }

        // 4. Собираем результат
        return KmType().apply {
            classifier?.let { this.classifier = it }
            this.arguments.addAll(args)

            this.isNullable = this@toKmType.isNullable()
        }
    }

    fun IrValueParameter.toKmType() = KmValueParameter(name.asString()).apply {
        this.isCrossinline = this@toKmType.isCrossinline
        this.isNoinline = this@toKmType.isNoinline
        this.varargElementType = this@toKmType.varargElementType?.toKmType()
        this.type = this@toKmType.type.toKmType()
        this.declaresDefaultValue = this@toKmType.hasDefaultValue()
        this.hasAnnotations = this@toKmType.annotations.isNotEmpty()
    }

    // Конвертер вариативности
    private fun Variance.toKmVariance() = when (this) {
        Variance.INVARIANT -> KmVariance.INVARIANT
        Variance.IN_VARIANCE -> KmVariance.IN
        Variance.OUT_VARIANCE -> KmVariance.OUT
    }
}
