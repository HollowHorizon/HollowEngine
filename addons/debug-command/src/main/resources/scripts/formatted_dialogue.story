# Форматирование

Виталик: Я теперь <b>жирный</b>, бойся меня!
Виталик: Можно говорить <i>курсивом</i>, <u>подчёркивать</u> и <s>зачёркивать</s> слова.
Виталик: Цвет бывает <color=red>именованным</color> или <color=#55FFFF>точным RGB</color>.
Виталик: Теги вкладываются: <color=gold>золотой и <b>золотой жирный</b></color>.
Виталик: <rainbow speed=0.35>Радуга</rainbow>, <wave amplitude=2 speed=3>волна</wave> и <shake amplitude=1.2>дрожь</shake> используют эффекты Hollow UI.
Виталик: Разметка не ломает паузы[-] и продолжает работать <b>после них</b>.

@choice "<b>Впечатляет!</b>" id=impressed
    Виталик: <pulse frequency=1.2>Я знал, что тебе понравится.</pulse>
@choice "<color=yellow>А можно ещё ярче?</color>" id=brighter
    Виталик: <gradient from=#FF5555 to=#5555FF speed=0.25>Вот так достаточно ярко?</gradient>
@choice "Показать экранирование" id=escaping
    Виталик: Напиши &lt;b&gt;текст&lt;/b&gt;, чтобы показать теги буквально.

Виталик: <glitch intensity=1 chromatic=false>Демонстрация завершена.</glitch>
