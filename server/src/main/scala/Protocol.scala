package Protocol;
import spray.json._

case class Protocal(function: String, data: JsValue)

abstract class ProtocolData
case class Message(message: String) extends ProtocolData
case class Login(username: String, password: String) extends ProtocolData
case class Register(username: String, password: String) extends ProtocolData
case class UploadFile(filename: String) extends ProtocolData
case class DownloadFile(filename: String, url: String) extends ProtocolData

object MyJsonProtocol extends DefaultJsonProtocol {
  implicit val ProtocalFormat = jsonFormat2(Protocal)

  implicit val MessageFormat = jsonFormat1(Message)
  implicit val loginFormat = jsonFormat2(Login)
  implicit val RegisterFormat = jsonFormat2(Register)
  implicit val UploadFileFormat = jsonFormat1(UploadFile)
  implicit val DownloadFileFormat = jsonFormat2(DownloadFile)
}

import MyJsonProtocol._

object ParseRequest {
  def parse(jsonStr: String): ProtocolData = {
    try {
      val jsonValue = JsonParser(jsonStr)
      val wholeProtocal = jsonValue.convertTo[Protocal]
      val function = wholeProtocal.function
      val data = wholeProtocal.data
      function match {
        case "login" => data.convertTo[Login]
        case "message" => data.convertTo[Message]
        case "upload" => data.convertTo[UploadFile]
        case "download" => data.convertTo[DownloadFile]
        case "register" => data.convertTo[Register]
        case _ => null
      }
    }
    catch {
      case e: Exception => { println("parsing error"); null}
    }
  }
}

