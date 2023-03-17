import ru.hollowhorizon.hc.client.utils.toSTC
import ru.hollowhorizon.hollowstory.dialogues.HDCharacter

val alek = HDCharacter("guardvillagers:guard", "Стражник")

var result = -1
play("hollowstory:al1")
alek say "Так, а ну стоять!"
play("hollowstory:al2")
alek say "Ты только вчера появился в нашем поселении? и уже половина деревни на тебя жалуется, вор!"
play("hollowstory:al3")
alek say "Так что у тебя два пути: либо ты как можно быстрее уходишь отсюда, чтобы ноги твоей здесь больше не было"
alek say "либо будешь иметь дело с големами!"

choice(
    "Извиниться" to {
        player say "Прошу меня простить, я очень голоден и беден, и был вынужден украсть немного еды, чтобы выжить."
        play("hollowstory:al4")
        alek say "То, что ты беден, ничего не значит, закон един для всех."
        play("hollowstory:al5")
        alek say "Однако, то, что ты сам признал свою вину это хорошо."
        play("hollowstory:al6")
        alek say "Советую тебе скрыться с глаз и больше не появляться в этом поселении, если не хочешь проблем."
        result = 0
    },
    "Промолчать" to {
        player say "..."
        play("hollowstory:al7")
        alek say "Ну, чего молчишь?"
        play("hollowstory:al8")
        alek say "Сказать нечего?"
        play("hollowstory:al10")
        alek say "Стыдно за содеянное? Ворьё, лучше уходи из деревни, пока цел."
        result = 0
    },
    "Отрицать" to {
        player say "Что?"
        player say "Я Вас не понимаю?"
        player say "Что это за обвинения без доказательств?"
        player say "Я ничего не крал!"
        play("hollowstory:al11")
        alek say "Мало того, что обворовал здесь чуть ли не каждого второго, так ещё и врёшь, говоря, что не виновен?"
        play("hollowstory:al12")
        alek say "Все видели, как ты воруешь, так что даже не пытайся лгать!"
        play("hollowstory:al13")
        alek say "Уходи отсюда прочь!"
        result = 1
    },
    "Угроза" to {
        player say "Ты ещё кто такой?"
        player say "А ну пшёл вон, если не хочешь неприятностей на свою голову!"
        play("hollowstory:al14")
        alek say "Ах ты, преступное отродье! Ну всё, ты явно напросился…"
        result = 1
    }
)

val marko = HDCharacter("minecraft:villager", "Фермер", "{VillagerData:{profession:farmer,level:2,type:savanna}}")

play("hollowstory:ma1")
marko say "Алек! Ну что же Вы как всегда начинаете?"
alek.name = "Алек".toSTC()
play("hollowstory:ma2")
marko say "Ничего же серьёзного не случилось, никто же не умер и не пострадал."
play("hollowstory:ma3")
marko say "А наш гость слаб и голоден."
play("hollowstory:ma4")
marko say "Лучше оказать ему радушный приём, к тому же пара рабочих рук лишней никогда не бывает."

if(result == 0) {
    play("hollowstory:al15")
    alek say "Ладно, будь по-твоему, Марко. Путник, ты можешь остаться здесь, на какое-то время, но помни, я слежу за тобой."
} else {
    play("hollowstory:al16")
    alek say "Ладно. Так уж и быть, Марко. Чужеземец, ты можешь остаться здесь, на какое-то время, но если я увижу твою руку в чужом сундуке — отрублю!"
}

marko.name = "Марко".toSTC()

scene remove alek

play("hollowstory:ma5")
marko say "Ну, а ты чего встал-то как вкопанный? Лопату в руки и вперёд, работать! Поможешь мне дорожки сделать. Идём, я покажу."

choice("Согласиться" to {})