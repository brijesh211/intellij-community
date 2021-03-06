package com.intellij.filePrediction

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.openapi.project.Project

internal object FileNavigationLogger {
  private const val GROUP_ID = "file.prediction"

  fun logEvent(project: Project,
               event: String,
               features: FileFeaturesComputationResult,
               filePath: String,
               refsComputation: Long) {
    val data = FeatureUsageData().
      addAnonymizedPath(filePath).
      addData("refs_computation", refsComputation).
      addData("features_computation", features.duration)

    for (feature in features.value) {
      feature.value.addToEventData(feature.key, data)
    }
    FUCounterUsageLogger.getInstance().logEvent(project, GROUP_ID, event, data)
  }
}