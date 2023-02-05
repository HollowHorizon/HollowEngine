package ru.hollowhorizon.hollowstory.client.screen.widget.action

import net.minecraft.nbt.CompoundNBT

interface IProcedureLoader {
    fun saveNBT(): CompoundNBT
    fun loadNBT(nbt: CompoundNBT)
}