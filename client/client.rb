require 'socket'
require 'json'
require 'curses'
require_relative 'request'

module SERVER
  PORT = 2630
  HOST = 'localhost'
end

class History < Curses::Window
  def init
    scrollok true
  end

  def update(message)
    addstr(message)
    if cury > maxy - 2
      scrl 1 
      setpos(cury, 0)
    else
      setpos(cury + 1, 0)
    end
    refresh
  end
end

class UI
  def initialize(socket)
    @socket = socket
  end

  def init
    Curses.init_screen
    Curses.cbreak
    Curses::nonl
    @window = Curses::Window.new(0, 0, 0, 0)
    @window.box("|", "-")
    @window.setpos(@window.maxy - 3, 1)
    @window.addstr("-" * (@window.maxx - 3))
    @window.setpos(3,3)
    @window.refresh
    @window.keypad true

    init_history
  end

  def start
    handle_keyboard
  end

  def init_history
    @history = History.new(@window.maxy - 5, @window.maxx - 2, 1, 1)
    @history.init
    # @history = @window.subwin(@window.maxy - 5, @window.maxx - 2, 1, 1)
    @history.refresh
  end

  def message_posx; 1; end
  def message_posy; @window.maxy - 2; end

  def clear_message
    @window.setpos(message_posy, message_posx)
    @window.addstr(" " * (@window.maxx - 3))
    @window.refresh
    @window.setpos(message_posy, message_posx)
  end

  def handle_keyboard
    @window.setpos(message_posy, message_posx)
    str = []
    loop do
      # 此處與fresh同時動到x, y位置，需lock
      c = @window.getch 

      case c
      when Curses::KEY_UP
        @history.scrl 1
        @history.setpos(@history.cury - 1, 0)
        @history.refresh
        next
      when Curses::KEY_DOWN
        # 無法保留歷史，需再自己保存
        @history.scrl -1
        @history.setpos(@history.cury + 1, 0)
        @history.refresh
        next
      end

      if (not c.respond_to?(:ord)) and c.class != Fixnum; puts "#{c} #{c.class}";sleep(3) end
      
      if c.ord == 13   # 偵測 enter
        str = str.pack("C*").force_encoding("UTF-8")
        case str
        when /:f [^ ]*/
          filename = str[3..-1]
          if File.exist?(filename)
            @socket.puts Request.make_upload(filename)
          else
            fresh_history "file #{filename} not exist"
          end
        when /:d [^ ]+ [^ ]+/
          arg = str[3..-1].split
          @socket.puts Request.make_download(arg[0], arg[1])
        else
          @socket.puts Request.make_message(str)
          fresh_history str
        end
        str = []
        clear_message
      elsif c.class == Fixnum
        str << c
      else
        str << c.ord
      end
    end
  end

  def fresh_history(message)
    @history.update(message)
  end
end

class Client
  def get_login_request
    print "使用者名稱："
    @username = $stdin.gets.chomp
    print "密碼："
    @password = $stdin.gets.chomp
    req = JSON.generate({function: "login", data: {username: @username, password: @password}})
  end

  def initialize()
    if ARGV.length != 2
      @host, @port = SERVER::HOST, SERVER::PORT
    else
      @host, @port = ARGV[0], ARGV[1]
    end

    req = get_login_request
    @socket = TCPSocket.new(@host, @port)
    @socket.puts req
    @ui = UI.new(@socket)
  end

  def upload_file(filename, port, password)
    file_socket = TCPSocket.new(@host, port)
    file_socket.puts password
    File.open(filename, "r") do |f|
      f.readlines.each do |line|
        file_socket.print line
      end
    end
    file_socket.close
    @ui.fresh_history "upload finished"
  end

  def download_file(filename, port, password)
    file_socket = TCPSocket.new(@host, port)
    file_socket.puts password
    File.open(filename, "w") do |f|
      file_socket.each_line do |l|
        f.print l
      end
    end
    file_socket.close
  end

  def handle_response(response)
    res_json = JSON.parse(response)
    case res_json["function"]
    when "message"
      @ui.fresh_history res_json["data"]["message"]
    when "upload"
      @ui.fresh_history "uploading"
      Thread.new {
        data = res_json["data"]
        upload_file(data["filename"], data["port"], data["password"])
      }
    when "download"
      Thread.new {
        data = res_json["data"]
        download_file(data["filename"], data["port"], data["password"])
      }
    end
  end

  def start
    Thread.new {
      while true
        response = @socket.gets
        handle_response response
      end
    }
    @ui.init
    @ui.start

  end

end

Client.new.start
