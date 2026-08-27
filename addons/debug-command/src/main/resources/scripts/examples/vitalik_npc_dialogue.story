# Встреча

@look-at Виталик player
@play-sound minecraft:entity.villager.ambient 0.6 1.15

@if visits == 1
    Виталик: <color=gold>О, новое лицо!</color> Я Виталик.
@else
    Виталик: Снова ты! Это уже наша <b>{visits}-я</b> встреча.

Виталик: Я теперь <b>жирный</b>, бойся меня!
Виталик: А ещё умею говорить <i>курсивом</i> и смотреть прямо на собеседника.

@choice "<b>Кто ты такой?</b>" id=about
    Виталик: Я настоящий NPC HollowEngine: появился из Kotlin-скрипта и запустил этот диалог по ПКМ.
@choice "<color=#55FFFF>Покажи эффекты</color>" id=effects
    Виталик: <rainbow speed=0.35>Радуга</rainbow>, <wave amplitude=2 speed=3>волна</wave> и <shake amplitude=1.2>дрожь</shake>.
@choice "<i>Мне пора</i>" id=leave
    Виталик: <color=gray>До встречи, {player.name}.</color>

Виталик: <gradient from=#FFAA00 to=#55FFFF speed=0.25>Изменяй оба файла в IDE и запускай ноду заново.</gradient>
@stop-looking Виталик
