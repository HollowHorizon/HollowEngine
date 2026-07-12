package ru.hollowhorizon.hollowengine.addons.video

import com.mojang.brigadier.arguments.StringArgumentType
import kotlinx.coroutines.CoroutineScope
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import ru.hollowhorizon.hollowengine.addons.video.api.HollowVideo
import ru.hollowhorizon.hollowengine.addons.video.api.VideoSourceResolver
import ru.hollowhorizon.hollowengine.api.VideoApi
import ru.hollowhorizon.hollowengine.common.addons.HollowAddonContext
import ru.hollowhorizon.hollowengine.common.addons.HollowAddonEntrypoint
import ru.hollowhorizon.hollowengine.common.addons.publish
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.registry.RegisterCommandsEvent

class HollowVideoAddon : HollowAddonEntrypoint {
    private var video: HollowVideo? = null

    override suspend fun load(context: HollowAddonContext, scope: CoroutineScope) {
        video = HollowVideo(scope).also {
            context.hostServices.publish<VideoApi>(it)
        }
    }

    override suspend fun unload(context: HollowAddonContext) {
        video?.close()
        video = null
    }

    @SubscribeEvent
    fun registerCommands(event: RegisterCommandsEvent) {
        val hollowEngineCommand = requireNotNull(
            event.dispatcher.root.getChild("hollowengine"),
        ) {
            "The HollowEngine root command must be registered before addon commands"
        }

        val videoCommand = Commands.literal("video")
            .then(
                Commands.argument<String>(
                    "source",
                    StringArgumentType.string(),
                ).executes { command ->
                    val activeVideo = video

                    if (activeVideo == null) {
                        command.source.sendFailure(
                            Component.literal(
                                "The HollowEngine video addon is not active.",
                            ),
                        )
                        return@executes 0
                    }

                    val source = runCatching {
                        VideoSourceResolver.resolve(
                            StringArgumentType.getString(command, "source"),
                        )
                    }.getOrElse { error ->
                        command.source.sendFailure(
                            Component.literal(
                                "Invalid video source: ${error.message}",
                            ),
                        )
                        return@executes 0
                    }

                    activeVideo.play(source)

                    command.source.sendSuccess(
                        { Component.literal("Opening video: $source") },
                        false,
                    )

                    1
                },
            )

        hollowEngineCommand.addChild(videoCommand.build())
    }
}
