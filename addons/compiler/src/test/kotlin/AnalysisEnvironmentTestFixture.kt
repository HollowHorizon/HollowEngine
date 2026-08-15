import ru.hollowhorizon.hollowengine.common.ScriptingEnvironmentImpl
import java.io.File

internal class AnalysisEnvironmentTestFixture(
    private val createEnvironment: () -> ScriptingEnvironmentImpl,
) {
    lateinit var environment: ScriptingEnvironmentImpl
        private set

    fun start() {
        environment = createEnvironment()
    }

    fun reset() {
        val analyzer = environment.analyzer
        val files = analyzer.fileCache.values.flatMap { cached -> cached.relatedFiles }
        analyzer.fileCache.clear()
        files.asReversed().forEach(analyzer::cleanupFile)
        File("hollowengine/scripts").deleteRecursively()
    }

    fun close() {
        environment.close()
        File("hollowengine").deleteRecursively()
    }
}
