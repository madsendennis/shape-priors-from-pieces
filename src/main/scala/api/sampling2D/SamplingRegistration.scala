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

package api.sampling2D

import java.io.File

import api.sampling2D.loggers.JSONAcceptRejectLogger
import apps.scalismoExtension.{FormatConverter, LineMeshMetrics2D}
import breeze.linalg.DenseVector
import com.typesafe.scalalogging.Logger
import scalismo.geometry._
import scalismo.mesh.{LineMesh, LineMesh2D}
import scalismo.sampling.DistributionEvaluator
import scalismo.sampling.algorithms.MetropolisHastings
import scalismo.sampling.loggers.ChainStateLogger.implicits._
import scalismo.sampling.loggers.{BestSampleLogger, ChainStateLoggerContainer}
import scalismo.sampling.proposals.MixtureProposal
import scalismo.statisticalmodel.PointDistributionModel
import scalismo.ui.api.SimpleAPI
import scalismo.utils.Random


class SamplingRegistration(model: PointDistributionModel[_2D, LineMesh], sample: LineMesh2D, modelUi: Option[SimpleAPI] = None, modelUiUpdateInterval: Int = 1000, acceptInfoPrintInterval: Int = 10000) {
  implicit val random: Random = Random(1024)

  private val rotatCenter2D: EuclideanVector[_2D] = model.reference.pointSet.points.map(_.toVector).reduce(_ + _) * 1.0 / model.reference.pointSet.numberOfPoints.toDouble
  private val initPoseParameters = PoseParameters(EuclideanVector2D(0, 0), 0, rotationCenter = rotatCenter2D.toPoint)
  private val initShapeParameters = ShapeParameters(DenseVector.zeros[Double](model.rank))
  private val initialParametersZero = ModelFittingParameters(initPoseParameters, initShapeParameters)

  def runfitting(evaluators: Map[String, DistributionEvaluator[ModelFittingParameters]], generator: MixtureProposal.ProposalGeneratorWithTransition[ModelFittingParameters], numOfSamples: Int, initialModelParameters: Option[ModelFittingParameters] = None, jsonName: File = new File("tmp.json")): ModelFittingParameters = {
    val logger: Logger = Logger(s"MCMC-${jsonName.getName}")

    val initialParameters = initialModelParameters.getOrElse(initialParametersZero)

    val acceptRejectLogger = new JSONAcceptRejectLogger[ModelFittingParameters](jsonName, Option(evaluators))

    val evaluator = evaluators("product")

    val chain: MetropolisHastings[ModelFittingParameters] = MetropolisHastings(generator, evaluator)

    val bestSamplelogger = BestSampleLogger(evaluator)

    val mhIt = chain.iterator(initialParameters, acceptRejectLogger) loggedWith ChainStateLoggerContainer(Seq(bestSamplelogger))

    val sampleGroup = if (modelUi.isDefined) {
      Option(modelUi.get.createGroup("sampleGroup"))
    }
    else {
      None
    }

    val samplingIterator = for ((theta, i) <- mhIt.zipWithIndex) yield {
      if (i % modelUiUpdateInterval == 0 && i != 0) {
        logger.debug(" index: " + i + " LOG: " + bestSamplelogger.currentBestValue().get)
        val thetaToUse = if (acceptRejectLogger.logSamples.nonEmpty) {
          acceptRejectLogger.logSamples.last // Get last accepted sample
          //            bestSamplelogger.currentBestSample().get  // Get overall best sample
        }
        else {
          theta
        }
        val currentSample = FormatConverter.lineMesh2Dto3D(ModelFittingParameters.transformedMesh(model, thetaToUse))
        if (modelUi.isDefined) {
          modelUi.get.show(sampleGroup.get, currentSample, s"${i.toString}")
        }
      }
      if (i % acceptInfoPrintInterval == 0 && i != 0) {
        acceptRejectLogger.writeLog()
        acceptRejectLogger.printAcceptInfo(jsonName.getName)
        val bestTheta = bestSamplelogger.currentBestSample().get
        val bestStuff: LineMesh[_2D] = ModelFittingParameters.transformedMesh(model, bestTheta)
        LineMeshMetrics2D.evaluateReconstruction2GroundTruthBoundaryAware("Sampling", sample, bestStuff)
      }
      theta
    }
    samplingIterator.take(numOfSamples).toSeq.last

    logger.info("Done fitting - STATS:")
    acceptRejectLogger.writeLog()
    acceptRejectLogger.printAcceptInfo()

    val bestSampleCoeff: ModelFittingParameters = bestSamplelogger.currentBestSample().get
    bestSampleCoeff
  }
}

