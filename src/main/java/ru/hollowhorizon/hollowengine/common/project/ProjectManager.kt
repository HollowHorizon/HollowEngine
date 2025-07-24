package ru.hollowhorizon.hollowengine.common.project

import de.fabmax.kool.KeyValueStore

object ProjectManager {
    var currentProject: Project? = KeyValueStore.loadString("project_manager.active_project")
        ?.let { Project.fromName(it) }

    fun loadProject(name: String): Project {
        val project = Project.fromName(name)
        currentProject = project
        KeyValueStore.storeString("project_manager.active_project", name)
        return project
    }

    fun removeProject(name: String) {
        if (currentProject?.name == name) {
            currentProject = null
            KeyValueStore.delete("project_manager.active_project")
        }
        Project.fromName(name).delete()
    }
}