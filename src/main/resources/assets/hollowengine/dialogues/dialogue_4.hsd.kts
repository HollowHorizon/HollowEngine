import net.minecraft.client.Minecraft
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import ru.hollowhorizon.hollowengine.dialogues.HDCharacter

val marko = HDCharacter("minecraft:villager", "Марко", "{VillagerData:{profession:farmer,level:2,type:savanna}}")

play("hollowstory:ma7")
marko say "О! Я вижу, ты уже всё сделал. Хвалю, хвалю."
marko say "Устал, наверное. Вы молодые хоть и полны сил и энергии, тоже ведь в отдыхе нуждаетесь."
marko say "Пойдём ко мне в дом, накормлю вкусным ужином."

choice("В дом Марко" to {})

setBackground("hollowstory:textures/gui/bg/house.png")

play("hollowstory:ma8")
marko say "Располагайся, чувствуй себя как дома, я сейчас всё приготовлю, а ты мне лучше расскажи откуда, куда путь держишь."
marko say "Не видал я тебя в здешних краях. У тебя необычная одежда."
player say "Да, я можно сказать, не из здешних краёв."
play("hollowstory:ma9")
marko say "Вы путешественник? Редко можно встретить таких личностей в наших краях."
marko say "Это какими же судьбами ты здесь оказался? Ищешь приключения?"
player say "Я исследователь и в данный момент занимаюсь поиском древней цивилизации кобольдов."

choice("Дорассказать историю" to {})

play("hollowstory:ma10")
marko say "Ох, неужели вас тоже коснулись эти ужасные мародёры?"
marko say "Недавно они и на нашу деревню покусились, забрали мою дорогую и единственную дочь!"
marko say "Мне говорят, забудь Марко, её скорее всего уже нет в живых, или её переправили в другое место в роли слуги или раба..."
marko say "Но моё отцовское сердце чует! Моя дочь жива!"

choice("Продолжить" to {})

play("hollowstory:ma11")
marko say "Может путешественник окажет мне услугу? Не могли бы вы пробраться к разбойникам и попытаться найти и вернуть мою дочь?"
play("hollowstory:ma12")
marko say "Я могу договориться насчёт оружия и брони! А после, клянусь, в долгу уж точно не останусь."

choice(
    "Согласиться" to {
        player say "Даже не знаю…"
        player say "Идти в логово разбойников прямо сейчас…"
        player say "Ничего не могу обещать, но постараюсь вам помочь."
        play("hollowstory:ma13")
        marko say "Спасибо! Спасибо! Я вам отплачу! Отплачу по полной, лишь бы вновь увидеть мою доченьку! "
        play("hollowstory:ma14")
        marko say "Знаешь, я могу замолвить за тебя словечко картографу, чтобы он нарисовал тебе карту…"
        play("hollowstory:ma15")
        marko say "Да, точно! Иначе за “спасибо” он тебе ничего не сделает! Он у нас такой, непростой!"
        play("hollowstory:ma16")
        marko say "У меня тут завалялась одна вещица… Мне она вряд ли уже понадобится."
        marko say "Это мой любимый лук, я очень любил с ним охотиться, но старость дает о себе знать. Тебе он будет нужнее!"
        Minecraft.func_71410_x().field_71439_g!!.field_71071_by.func_70441_a(ItemStack(Items.field_151031_f))
        Minecraft.func_71410_x().field_71439_g!!.field_71071_by.func_70441_a(ItemStack(Items.field_151032_g, 32))
        play("hollowstory:ma17")
        marko say "Ладно, засиделись мы с тобой. До встречи."
    },
    "Отказать" to {
        player say "Не, Старик, ищи себе другого мальчика на побегушках."
        play("hollowstory:ma18")
        marko say "Ладно, прости, что вывалил на тебя это… А теперь, я бы хотел побыть один. Попрошу на выход."
    }
)