package mesosphere.chaos.examples.trains.impl.shunting

import org.scalatest.FlatSpec

import org.mockito.Mockito._

class ShuntingModuleTest extends FlatSpec {
  it should "work" in {
    // given
    val mockPointSwitcher = mock(classOf[PointSwitcher])

    // when
    val moduleToTest = new TraditionalShuntingModule {
      // the mock implementation will be used to wire the graph
      override lazy val pointSwitcher = mockPointSwitcher
    }
    moduleToTest.trainShunter.shunt()

    // then
    verify(mockPointSwitcher).switch()
  }
}
