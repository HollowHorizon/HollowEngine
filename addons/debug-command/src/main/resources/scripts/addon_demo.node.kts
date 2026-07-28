
import ru.hollowhorizon.hollowengine.HollowEngine

var elapsedSeconds = 0

onStart {
    HollowEngine.LOGGER.info("Addon script {} started with {} sec.", path, elapsedSeconds)
}

onUpdate(20.ticks) {
    elapsedSeconds += 1
}

onSave { context ->
    context.tag.putInt("elapsedSeconds", elapsedSeconds)
}

onLoad { context ->
    elapsedSeconds = context.tag.getInt("elapsedSeconds")
}
