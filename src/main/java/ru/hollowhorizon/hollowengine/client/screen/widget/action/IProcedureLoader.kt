package ru.hollowhorizon.hollowengine.client.screen.widget.action

import net.minecraft.nbt.CompoundNBT

interface IProcedureLoader {
    fun saveNBT(): CompoundNBT
    fun loadNBT(nbt: CompoundNBT)
}