var Proxy = require('http-mitm-proxy');
var proxy = Proxy();

proxy.onError(function(ctx, err) {
  console.error('proxy error:', err);
});

proxy.onRequest(function(ctx, callback) {
  if (ctx.clientToProxyRequest.headers.host == 'www.example.com') {
    ctx.use(Proxy.gunzip);

    ctx.onResponseData(function(ctx, chunk, callback) {
      chunk = new Buffer(chunk.toString().replace(/<h1.*?<\/h1>/g, '<h3>Pwned!</h3>'));
      return callback(null, chunk);
    });
  }
  return callback();
});

proxy.listen({port: 8081});



var http = require('http'),
    https = require('https'),
    connect = require('connect'),
    httpProxy = require('http-proxy');


var selects = [];
var simpleselect = {};

//<img id="logo" src="/images/logo.svg" alt="node.js">
simpleselect.query = 'h1';
simpleselect.func = function (node) {
    
    //Create a read/write stream wit the outer option 
    //so we get the full tag and we can replace it
    var stm = node.createStream({ "outer" : true });

    //variable to hold all the info from the data events
    var tag = '';

    //collect all the data in the stream
    stm.on('data', function(data) {
       tag += data;
    });

    //When the read side of the stream has ended..
    stm.on('end', function() {

      //Print out the tag you can also parse it or regex if you want
      process.stdout.write('tag:   ' + tag + '\n');
      process.stdout.write('end:   ' + node.name + '\n');
      
      //Now on the write side of the stream write some data using .end()
      //N.B. if end isn't called it will just hang.  
      stm.end('<img id="logo" src="http://i.imgur.com/LKShxfc.gif" alt="node.js">');      
    
    });    
}

selects.push(simpleselect);

//
// Basic Connect App
//
var app = connect();

var proxy = httpProxy.createProxyServer({
   target: 'http://localhost',
   port: 8081,
   headers:{ host: 'localhost' }
})
var proxy2 = httpProxy.createProxyServer({
   target: 'http://example.org',
   headers:{ host: 'example.org' }
})


app.use(require('harmon')([], selects, true));

app.use(
  function (req, res) {
    proxy.web(req, res);
  }
);

http.createServer(app).listen(8002);

