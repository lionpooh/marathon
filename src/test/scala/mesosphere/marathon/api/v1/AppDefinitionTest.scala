package mesosphere.marathon.api.v1

import org.junit.Test
import org.junit.Assert._
import com.google.common.collect.Lists
import scala.collection.JavaConverters._
import mesosphere.marathon.Protos.ServiceDefinition
import org.apache.mesos.Protos.CommandInfo
import javax.validation.Validation

/**
 * @author Tobi Knaup
 */
class AppDefinitionTest {

  @Test
  def testToProto() {
    val app = new AppDefinition
    app.id = "play"
    app.cmd = "bash foo-*/start -Dhttp.port=$PORT"
    app.cpus = 4
    app.mem = 256
    app.instances = 5
    app.ports = Seq(8080, 8081)
    app.executor = "//cmd"

    val proto = app.toProto
    assertEquals("play", proto.getId)
    assertEquals("bash foo-*/start -Dhttp.port=$PORT", proto.getCmd.getValue)
    assertEquals(5, proto.getInstances)
    assertEquals(Lists.newArrayList(8080, 8081), proto.getPortsList)
    assertEquals("//cmd", proto.getExecutor)
    assertEquals(4, getScalarResourceValue(proto, "cpus"), 1e-6)
    assertEquals(256, getScalarResourceValue(proto, "mem"), 1e-6)
    // TODO test CommandInfo
  }

  @Test
  def testMergeFromProto() {
    val cmd = CommandInfo.newBuilder
      .setValue("bash foo-*/start -Dhttp.port=$PORT")

    val proto = ServiceDefinition.newBuilder
      .setId("play")
      .setCmd(cmd)
      .setInstances(3)
      .setExecutor("//cmd")
      .build

    val mergeResult = AppDefinition().mergeFromProto(proto)

    assertEquals("play", mergeResult.id)
    assertEquals(3, mergeResult.instances)
    assertEquals("//cmd", mergeResult.executor)
    assertEquals("bash foo-*/start -Dhttp.port=$PORT", mergeResult.cmd)
  }

  @Test
  def testValidation() {
    val validator = Validation.buildDefaultValidatorFactory().getValidator

    def shouldViolate(app: AppDefinition, path: String, template: String) {
      val violations = validator.validate(app).asScala
      assertTrue(violations.exists(v =>
        v.getPropertyPath.toString == path && v.getMessageTemplate == template))
    }

    def shouldNotViolate(app: AppDefinition, path: String, template: String) {
      val violations = validator.validate(app).asScala
      assertFalse(violations.exists(v =>
        v.getPropertyPath.toString == path && v.getMessageTemplate == template))
    }

    val app = new AppDefinition
    app.id = "a b"
    shouldViolate(app, "id", "{javax.validation.constraints.Pattern.message}")
    app.id = "a#$%^&*b"
    shouldViolate(app, "id", "{javax.validation.constraints.Pattern.message}")
    app.id = "ab"
    shouldNotViolate(app, "id", "{javax.validation.constraints.Pattern.message}")
  }

  @Test
  def testSerialization() {
    import com.fasterxml.jackson.databind.ObjectMapper
    import com.fasterxml.jackson.module.scala.DefaultScalaModule
    import mesosphere.marathon.api.v2.json.MarathonModule
    import mesosphere.marathon.api.v1.json.ConstraintModule

    val mapper = new ObjectMapper
    mapper.registerModule(DefaultScalaModule)
    mapper.registerModule(new MarathonModule)
    mapper.registerModule(new ConstraintModule)

    val original = AppDefinition()
    val json = mapper.writeValueAsString(original)
    val readResult = mapper.readValue(json, classOf[AppDefinition])

    assertTrue(readResult == original)
  }

  def getScalarResourceValue(proto: ServiceDefinition, name: String) = {
    proto.getResourcesList.asScala
      .find(_.getName == name)
      .get.getScalar.getValue
  }
}
