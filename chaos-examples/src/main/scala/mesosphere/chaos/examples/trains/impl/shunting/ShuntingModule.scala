package mesosphere.chaos.examples.trains.impl.shunting

import com.softwaremill.macwire._

class PointSwitcher()
class TrainCarCoupler()

trait TrainShunter

class TraditionalTrainShunter(pointSwitcher: PointSwitcher,
                              trainCarCoupler: TrainCarCoupler) extends TrainShunter

class TeleportingTrainShunter() extends TrainShunter

trait ShuntingModule {
  lazy val pointSwitcher = wire[PointSwitcher]

  def trainShunter: TrainShunter
}

trait TraditionalShuntingModule extends ShuntingModule {
  lazy val trainCarCoupler = wire[TrainCarCoupler]
  lazy val trainShunter = wire[TraditionalTrainShunter]
}

trait ModernShuntingModule extends ShuntingModule {
  lazy val trainShunter = wire[TeleportingTrainShunter]
}
