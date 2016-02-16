package mesosphere.chaos.examples.trains.impl.loading

import com.softwaremill.macwire._

import mesosphere.chaos.examples.trains.impl.shunting.PointSwitcher

trait LoadingModule {
  lazy val craneController = wire[CraneController]
  lazy val trainLoader = wire[TrainLoader]

  // dependency of the module
  def pointSwitcher: PointSwitcher
}

class CraneController()
class TrainLoader(craneController: CraneController,
                  pointSwitcher: PointSwitcher)
