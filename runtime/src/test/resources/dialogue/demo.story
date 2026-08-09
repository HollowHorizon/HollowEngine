# Начало

@look-at Виталик player
Виталик: Стой. Дальше без пропуска нельзя.

@if пропуск
    Виталик: А, это опять ты. Проходи.
    @jump #Пропустил

Виталик: У тебя с собой {money} монет.[-] Пропуск стоит 20.

@choice "Заплатить 20 монет" id=pay if=money>=20
    @set money = money - 20
    @set пропуск = true
    @play-once Виталик hello
    @play-sound minecraft:entity.villager.yes 1.0
    Виталик: Вот теперь другое дело.
    @jump #Пропустил
@choice "Поторговаться" id=haggle
    @jump #Торг
@choice "Уйти" id=leave
    Виталик: Иди-иди.
    @jump #Конец

# Торг

@async name=терпение
    @wait 12s
    @sync
    Виталик: Всё, надоел. Проваливай.
    @jump #Конец

Виталик: Ну попробуй.
@choice "Скидка за красивые глаза?"
    @cancel терпение
    Виталик: Не смеши.
    @jump #Начало
@choice "Я друг капитана."
    @cancel терпение
    @look-at Виталик [100, 65, 100] time=800
    Виталик: ...капитана, говоришь.
    @look-at Виталик player
    Виталик: Ладно, десять монет и мы не знакомы.
    @if money >= 10
        @set money = money - 10
        @set пропуск = true
        @jump #Пропустил
    Виталик: И этого нет? Иди отсюда.
    @jump #Конец

# Пропустил

@set пожитки = пожитки + "пропуск"

@hide-hud except=[chat, subtitle_overlay]
@fade-in 600ms #000000

@async name=отход
    // @walk-to Виталик [25, 70, -175] speed=1.2
    @look-at Виталик player

@camera location=[25, 70, -171] rotation=[-158, 2] time=1500 fov=60
@fade-out 600ms
@play-once Виталик hello

Виталик: Ворота открыты. Не задерживайся.

@await отход
@release-camera 800
@show-hud
@jump #Конец

# Конец

@if пожитки.size > 0
    Виталик: С собой у тебя: {пожитки[0]}.

@stop-looking Виталик
Виталик: Бывай.
