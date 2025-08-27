/*
 * MIT License
 *
 * Copyright (c) 2024 HollowHorizon
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ru.hollowhorizon.hollowengine.common.utils.nbt

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.internal.NamedValueDecoder
import kotlinx.serialization.internal.NamedValueEncoder
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag

@OptIn(InternalSerializationApi::class)
internal abstract class NamedValueTagEncoder : NamedValueEncoder(), ICanEncodeTag {
    final override fun encodeTag(tag: Tag) = encodeTaggedTag(popTag(), tag)
    abstract fun encodeTaggedTag(key: String, tag: Tag)
}

@OptIn(InternalSerializationApi::class)
internal abstract class NamedValueNbtDecoder : NamedValueDecoder(), ICanDecodeTag {
    final override fun decodeTag(): Tag = decodeTaggedTag(popTag())
    abstract fun decodeTaggedTag(key: String): Tag
}

internal interface ICanEncodeTag : ICanEncodeCompoundNBT {
    fun encodeTag(tag: Tag)
    override fun encodeCompoundNBT(tag: Tag) = encodeTag(tag)
}

internal interface ICanDecodeTag : ICanDecodeCompoundNBT {
    fun decodeTag(): Tag
    override fun decodeCompoundNBT(): CompoundTag = decodeTag() as CompoundTag
}

internal fun interface ICanEncodeCompoundNBT {
    fun encodeCompoundNBT(tag: Tag)
}

internal fun interface ICanDecodeCompoundNBT {
    fun decodeCompoundNBT(): CompoundTag
}

