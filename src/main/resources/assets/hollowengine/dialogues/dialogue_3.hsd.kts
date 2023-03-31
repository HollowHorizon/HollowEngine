import ru.hollowhorizon.hollowengine.dialogues.HDCharacter

val marko = HDCharacter("minecraft:villager", "Марко", "{VillagerData:{profession:farmer,level:2,type:savanna}}")

marko say "Ну, а ты чего встал-то как вкопанный?"
marko say "Лопату в руки и вперёд, работать!"
marko say "Поможешь мне дорожки сделать. Идём, я покажу."

choice(
    "Согласиться" to {
        player say "Да, конечно!"
        marko say "Отлично! Ну, а теперь идём."
    }
)