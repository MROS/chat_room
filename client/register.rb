require 'socket'
require 'json'
require_relative 'setting'

puts "歡迎註冊！！請照下方指示完成註冊。"
print "請輸入帳號："
username = $stdin.gets.chomp
print "請輸入密碼："
password = $stdin.gets.chomp
print "請重新輸入密碼："
password2 = $stdin.gets.chomp

if password == password2
  socket = TCPSocket.new(SERVER::HOST, SERVER::PORT)
  socket.puts(JSON.generate({function: "register", data: {username: username, password: password}}))
  socket.close
else
  puts "兩次密碼不相同，請重新運行本程式"
end
