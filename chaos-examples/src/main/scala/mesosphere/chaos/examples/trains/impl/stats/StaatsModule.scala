package mesosphere.chaos.examples.trains.impl.stats

import com.softwaremill.macwire._

import mesosphere.chaos.examples.trains.impl.loading.{LoadingModule, TrainLoader}
import mesosphere.chaos.examples.trains.impl.shunting.{ShuntingModule, TrainShunter}

class LoadingStats(trainLoader: TrainLoader)
class ShuntingStats(trainShunter: TrainShunter)

class StatsModule(
                   shuntingModule: ShuntingModule,
                   loadingModule: LoadingModule) {
  lazy val loadingStats = wire[LoadingStats]
  lazy val shuntingStats = wire[ShuntingStats]
}
