package ru.hollowhorizon.hollowstory.client.gui.widget.action

import net.minecraft.nbt.CompoundNBT

interface IProcedureLoader {
    fun saveNBT(): CompoundNBT
    fun loadNBT(nbt: CompoundNBT)
}