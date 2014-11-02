import java.net.{Socket, ServerSocket, InetAddress}
import java.io._
import java.sql.ResultSet
import scala.collection.mutable.ArrayBuffer
import scala.util.Random
import scala.io.Source._
import spray.json._
import Protocol._
import Protocol.MyJsonProtocol._

object DefaultSetting {
  val host = "localhost"
  val port = 2630;
  val database = "jdbc:mysql://localhost/chatRoom?user=chatRoom&password=xxxxx"
  val driver = "org.mysql.Driver"
  val dataPath = "data"
}


class HandleRequest(request: ProtocolData, socket: Socket
                    , socketArray: ArrayBuffer[Socket], username: String) extends Runnable {
  def printableString(len: Int): String = {
    val ans = new StringBuilder
    for (i <- 0 until len) {
      ans.append(((Random.nextInt() % 26).abs + 'a'.toInt).toChar)
    }
    ans.toString()
  }
  def insertFilenameToDB(filePath: String, url: String): Unit = {
    Class.forName("com.mysql.jdbc.Driver")
    val connection = java.sql.DriverManager.getConnection(DefaultSetting.database)
    val statement = connection.createStatement()
    statement.executeUpdate("INSERT INTO file (url, filename) VALUES ('" + url + "','" + filePath + "')")
    connection.close()
  }
  def getFilenameFromDB(url: String): String = {
    Class.forName("com.mysql.jdbc.Driver")
    val connection = java.sql.DriverManager.getConnection(DefaultSetting.database)
    val statement = connection.createStatement()
    val rs = statement.executeQuery(s"SELECT filename FROM file WHERE url = '$url' ")
    val ans =
      if (!rs.next()){
        null
      }
      else{
        rs.getString("filename")
      }
    connection.close()
    ans
  }
  def certificate(method: String, filename: String): (Boolean, Socket) = {
    val out = new PrintWriter(socket.getOutputStream)

    val fileSocket: ServerSocket = new ServerSocket(0)
    val port = fileSocket.getLocalPort
    val password = Random.nextString(16)
    out.println("{\"function\": \"" + method + "\", \"data\": {\"port\":" + port +
      ", \"password\": " + "\"" + password + "\"" + ", \"filename\":" + "\"" + filename +"\"}}")
    out.flush()

    val FileSocket = fileSocket.accept()
    fileSocket.close()

    val in = new BufferedReader(new InputStreamReader(FileSocket.getInputStream))
    val returnPassword = in.readLine()
    if (password == returnPassword) (true, FileSocket) else (false, null)
  }
  def run(): Unit = {
    request match {
      case Message(msg) => {
        println(username + ": " + msg)
        for (s <- socketArray) {
          if (s != socket) {
            s.synchronized {
              val out = new PrintWriter(s.getOutputStream)
              val response = Protocal("message", Message(username + ": " + msg).toJson).toJson.compactPrint
              out.println(response)
              out.flush()
            }
          }
        }
      }
      case UploadFile(filename) => {
        println(username + " want to upload " + filename)
        val (ok, readFileSocket) = certificate("upload", filename)
        if (ok) {
          println("password correct!!")

          val in = new BufferedReader(new InputStreamReader(readFileSocket.getInputStream))
          val filePath = DefaultSetting.dataPath + File.separator + filename
          val fileWriter = new PrintWriter(filePath, "UTF-8");

          while (!readFileSocket.isClosed) {
            val str = in.readLine()
            if (str == null) {
              fileWriter.flush()
              fileWriter.close()
              readFileSocket.close()
              println("readfile end")
            }
            else {
              println(str)
              fileWriter.println(str)
            }
          }

          var url = printableString(32)
          insertFilenameToDB(filePath, url)

          for (s <- socketArray) {
            s.synchronized {
              val out = new PrintWriter(s.getOutputStream)
              val response = Protocal("message", Message(s"${username} upload a file ${filename}, url is ${url}").toJson).toJson.compactPrint
              out.println(response)
              out.flush()
            }
          }
        }
        else {
          readFileSocket.close()
          println("password incorrect!! shutdown socket")
        }
      }
      case DownloadFile(filename, url) => {
        // filename其實不會用到，協定需要修改
        val filePath = getFilenameFromDB(url)
        if (filePath == null) {
          println("file not exist")
          // report to client
        }
        else {
          val (ok, writeFileSocket) = certificate("download", filename)
          if (ok){
            println("download password correct")
            val out = new PrintWriter(writeFileSocket.getOutputStream)
            val lines = fromFile(filePath).getLines
            for (l <- lines) {
              out.println(l)
            }
            out.flush()
            writeFileSocket.close()
          }
          else {
            writeFileSocket.close()
            println("download password incorrect")
          }
        }
      }
      case _ => {println("No this kind of request " + request)}
    }
  }
}

class HandleClient(socketArray: ArrayBuffer[Socket], socket: Socket) extends Runnable {

  def login: String = {
    val in = new BufferedReader(new InputStreamReader(socket.getInputStream))
    val requestStr = in.readLine()
    if (requestStr == null) {
      socket.close()
      ""
    }
    else {
      val request = ParseRequest.parse(requestStr)
      request match {
        case Login(username, password) => username
        case _ => {println("function is " + request); ""}
      }
    }
  }

  def run(): Unit = {
    val username = login
    if (username != "") println(username + " login")
    while (!socket.isClosed()) {
      val in = new BufferedReader(new InputStreamReader(socket.getInputStream))
      val msg = in.readLine()
      if (msg == null) {
        socket.close()
      }
      else {
        val request: ProtocolData = ParseRequest.parse(msg)
        (new Thread(new HandleRequest(request, socket, socketArray, username))).start
      }
    }
    // TODO: 依然有可能別的 HandleClient 用到未移除的socket，所以也必須做處理
    socketArray.synchronized {
      var i = 0
      var size = socketArray.length
      while (i < size) {
        if (socketArray(i) == socket) {
          size -= 1
          socketArray.remove(i)
        }
        else {
          i += 1
        }
      }
    }
  }
}

class ChatRoomServer(host: String, port: Int) {
  var socketArray = ArrayBuffer[Socket]()
  def listenConnect() {
    val server = new ServerSocket(port, 0, InetAddress.getByName(host))
    while (true) {
      val socket = server.accept
      socketArray.synchronized {
        socketArray += socket;
      }
      println("now socket Array is " + socketArray)
      (new Thread(new HandleClient(socketArray, socket))).start
    }
  }
  def start(): Unit = {
    listenConnect()
  }
}

object Main {
  def main(args: Array[String]) {
    for (s <- args) {
      println(s)
    }
    val server = 
      if (args.length != 0) 
        new ChatRoomServer(args(0), args(1).toInt) 
      else 
        new ChatRoomServer(DefaultSetting.host, DefaultSetting.port)
    server.start
  }
}
