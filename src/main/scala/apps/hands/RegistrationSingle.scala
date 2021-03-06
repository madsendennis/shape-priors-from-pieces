/*
 *  Copyright University of Basel, Graphics and Vision Research Group
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package apps.hands

import java.io.File

import scalismo.geometry._
import scalismo.io.{LandmarkIO, StatisticalModelIO}
import scalismo.ui.api.ScalismoUI

object RegistrationSingle extends App {
  scalismo.initialize()

  val modelFile = new File(Paths.handPath, "hand2D_gp_s25_s50_s120_per.h5")
  val model = StatisticalModelIO.readStatisticalLineMeshModel2D(modelFile).get
  val modelLMFile = new File(Paths.handPath, "reference-hand.json")
  val modelLM: Seq[Landmark[_2D]] = LandmarkIO.readLandmarksJson2D(modelLMFile).get

  val targetName = "hand-0"
  val finger = "thumb"
  val cutPercentage = 15

  val targetGTFile = new File(Paths.handPath, s"aligned/mesh/${targetName}.vtk")
  val partialName = s"${targetName}_${finger}_${cutPercentage}"
  val targetFile = new File(Paths.handPath, s"partial/mesh/${partialName}.vtk")
  val targetLMFile = new File(Paths.handPath, s"partial/landmarks/${partialName}.json")

  val logPath = Paths.handLogPath
  logPath.mkdir()
  val log = new File(logPath, s"${partialName}.json")

  val ui = ScalismoUI(partialName)

  val reg = HandRegistration(model, modelLM, targetGTFile, targetFile, targetLMFile, log)
  reg.run(ui = ui, numOfSamples = 10000, initialParameters = None, showNormals = false)
}
