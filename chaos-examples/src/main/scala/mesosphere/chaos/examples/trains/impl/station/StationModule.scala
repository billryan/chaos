package mesosphere.chaos.examples.trains.impl.station

import com.softwaremill.macwire._

import mesosphere.chaos.examples.trains.impl.loading.{LoadingModule, TrainLoader}
import mesosphere.chaos.examples.trains.impl.shunting.{TraditionalShuntingModule, ModernShuntingModule, ShuntingModule, TrainShunter}

// dependencies expressed using extends
trait StationModule extends ShuntingModule with LoadingModule {
  lazy val trainDispatch = wire[TrainDispatch]

  lazy val trainStation = wire[TrainStation]
}

class TrainDispatch()

class TrainStation(trainShunter: TrainShunter,
                   trainLoader: TrainLoader,
                   trainDispatch: TrainDispatch) {

  def prepareAndDispatchNextTrain() = "Choo choo"
}

object TrainStation extends App {
  val traditionalModules = new TraditionalShuntingModule
    with LoadingModule
    with StationModule

  val modernModules = new ModernShuntingModule
    with LoadingModule
    with StationModule

  println(traditionalModules.trainStation.prepareAndDispatchNextTrain())
  println(modernModules.trainStation.prepareAndDispatchNextTrain())
}