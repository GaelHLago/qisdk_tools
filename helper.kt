package com.softbankrobotics

import android.util.Log
import com.aldebaran.qi.Consumer
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.`object`.actuation.ExplorationMap
import com.aldebaran.qi.sdk.`object`.actuation.GoTo
import com.aldebaran.qi.sdk.`object`.actuation.LocalizationStatus
import com.aldebaran.qi.sdk.`object`.actuation.LocalizeAndMap
import com.aldebaran.qi.sdk.`object`.conversation.*
import com.aldebaran.qi.sdk.builder.*


class qiHelper {
    companion object {
        private val TAG = qiHelper::class.simpleName
    }

    lateinit var explorationMap: ExplorationMap

    fun createTopic(context: QiContext, resourceInt: Int): Future<Topic> {
        return TopicBuilder.with(context) // Create the builder using the QiContext.
                .withResource(resourceInt) // Set the topic resource.
                .buildAsync() // Build the topic.
    }

    fun createQiChatBotFuture(context: QiContext, topic: Topic): Future<QiChatbot> {
        Log.d(TAG, "Start createQiChatBotFuture")
        return QiChatbotBuilder.with(context)
                .withTopic(topic)
                .buildAsync()
    }

    fun runChat(context: QiContext, qiChatbot: QiChatbot): Future<Void> {
        Log.d(TAG, "Start runChat")
        return ChatBuilder.with(context)
                .withChatbot(qiChatbot)
                .buildAsync().andThenCompose { chat ->
                    Log.d(TAG, "run chat action")
                    chat.async().run()
                }
    }

    fun goToBookmark(qiChatbot: QiChatbot, bookmarkName: Bookmark): Future<Void> {
        Log.v(TAG, "goToBookmark")
        return qiChatbot.async().goToBookmark(bookmarkName, AutonomousReactionImportance.HIGH, AutonomousReactionValidity.DELAYABLE)
    }

    fun buildAndRunAnimation(context: QiContext, animationResource: Int): Future<Void> {
        Log.d(TAG, "Start buildAndRunAnimation")
        return AnimationBuilder.with(context)
                .withResources(animationResource)
                .buildAsync()
                .andThenCompose { animation ->
                    AnimateBuilder.with(context)
                            .withAnimation(animation)
                            .buildAsync().andThenCompose { animate ->
                                animate.async().run()
                            }
                }
    }


    fun createMapAndRunLocalize(qiContext: QiContext,
                                onMapGeneratedAndLocalizeRunning: () -> Unit) {
        Log.d(TAG, "Start createMapAndRunLocalize")
        // Mapping.
        val localizeAndMapBuildFuture = LocalizeAndMapBuilder.with(qiContext).buildAsync()
        localizeAndMapBuildFuture.andThenConsume(object : Consumer<LocalizeAndMap> {
            override fun consume(localizeAndMap: LocalizeAndMap?) {
                Log.v(TAG, "localizeAndMapBuildFuture.execute()")
                val runFuture = localizeAndMap!!.async().run()
                localizeAndMap.addOnStatusChangedListener { localizationStatus ->
                    Log.v(TAG, "localizeAndMapBuildFuture CHANGED $localizationStatus")
                    if (localizationStatus == LocalizationStatus.LOCALIZED) {
                        // Stop the action.
                        runFuture.requestCancellation()
                        // Dump the map for future use by a Localize action.
                        explorationMap = localizeAndMap.dumpMap()
                        // Run localize
                        runLocalize(qiContext, explorationMap) {
                            onMapGeneratedAndLocalizeRunning()
                        }
                    }
                }
            }
        })
    }

    fun runLocalize(qiContext: QiContext,
                    map: ExplorationMap,
                    onLocalizeRunning: () -> Unit) {
        Log.v(TAG, "Start runLocalize")
        val localizeFuture = LocalizeBuilder.with(qiContext).withMap(map).buildAsync()
        localizeFuture.andThenConsume { localize ->
            if (localize != null) {
                localize.async().run()
                onLocalizeRunning()
            }
        }
    }

    fun goTo(qiContext: QiContext, x: Double, y: Double, theta: Double): Future<GoTo> {
        Log.d(TAG, "Start goTo")
        val actuation = qiContext.actuation

        val robotFrame = actuation.robotFrame()

        val transform = TransformBuilder.create()
                .from2DTransform(x, y, theta)

        val mapping = qiContext.mapping

        val targetFrame = mapping.makeFreeFrame()

        targetFrame.update(robotFrame, transform, System.currentTimeMillis())

        return GoToBuilder.with(qiContext)
                .withFrame(targetFrame.frame())
                .buildAsync()

    }
}