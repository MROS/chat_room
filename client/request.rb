require 'json'

module Request
  class << self
    def make_message(str)
      JSON.generate({function: "message", data: {message: str}})
    end
    
    def make_upload(str)
      JSON.generate({function: "upload", data: {filename: str}})
    end

    def make_download(filename, url)
      JSON.generate({function: "download", data: {filename: filename, url: url}})
    end
  end

end
