package ru.hollowhorizon.hollowengine.client.gui.scripting

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color

fun UiScope.ServerIdeWarning() {
    modifier.backgroundColor(Color.BLACK.withAlpha(0.33f))
        .layout(CellLayout)

    Box {
        modifier.border(RectBorder(Color.WHITE, sizes.borderWidth))
            .backgroundColor(Color.BLACK.withAlpha(0.65f))
            .padding(sizes.smallGap)
            .align(AlignmentX.Center, AlignmentY.Center)

        Text(
            """
            Среда разработки на сервере отключена. На это есть несколько причин:
            - Не понятно, какие файлы и скрипты должны быть на сервере, а какие на клиенте.
            - При изменении клиентских скриптов что делать с теми игроками, у которых их нет? Пересылать им эти скрипты не безопасно.
            - Что делать с библиотеками и исходным кодом? Откуда его брать, с клиента или с сервера?
            Ну а также есть очень много мелких нюансов, вроде передачи больших файлов, синхронизации подсветки при работе нескольких игроков, прав доступа и т.п.
            
            Сам мод работает на сервере, а скрипты вы всегда можете перенести вручную. Но для редактирования пока что доступен только одиночный режим. 
            Если у вас есть мысли, как должен работать серверный редактор - напишите мне.
        """.trimIndent()
        ) {
            modifier.isWrapText(true).width(Grow.Std)
        }
    }
}