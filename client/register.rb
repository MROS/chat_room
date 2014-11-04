require 'socket'
require 'json'
require_relative 'setting'

if ARGV.length == 2
  host = ARGV[0]
  port = ARGV[1]
else
  host, port = SERVER::HOST, SERVER::PORT
end

puts "歡迎註冊！！請照下方指示完成註冊。"
print "請輸入帳號："
username = $stdin.gets.chomp
print "請輸入密碼："
password = $stdin.gets.chomp
print "請重新輸入密碼："
password2 = $stdin.gets.chomp

if password == password2
  socket = TCPSocket.new(host, port)
  socket.puts(JSON.generate({function: "register", data: {username: username, password: password}}))
  ok = socket.gets.chomp
  if ok == "OK"
    puts "註冊成功！！"
  else
    puts "註冊失敗，請換帳號"
  end
  socket.close
else
  puts "兩次密碼不相同，請重新運行本程式"
end
