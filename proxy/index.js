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
var a2 = connect();

var proxy2 = httpProxy.createProxyServer({
   target: 'http://localhost:8003',
   headers:{ host: 'localhost' }
})
var proxy = httpProxy.createProxyServer({
   target: 'http://example.org',
   headers:{ host: 'example.org' }
})

a2.use(
  function (req, res) {
    proxy2.web(req, res);
  }
);

app.use(require('harmon')([], selects, true));

app.use(
  function (req, res) {
    proxy.web(req, res);
  }
);

var fs = require('fs');
var logFile = fs.createWriteStream('./requests.log');

logger = function() {    
  // This will only run once
  var logFile = fs.createWriteStream('./requests.log');

  return function (request, response, next) { 
    // This will run on each request.
    logFile.write(JSON.stringify(request.headers, true, 2));
    next();
  }
}

http.createServer(a2).listen(8002);
http.createServer(app).listen(8003);

b = http;

var p4 = httpProxy.createProxyServer({target:'http://example.org:80'});
var s4 = http.createServer(function(req, res) {
  logFile.write(JSON.stringify(req.headers, true, 2));
  p4.web(req, res, { target: 'http://example.org:80' });
});

s4.listen(8004);

var p5 = httpProxy.createProxyServer({});
p5.on('proxyRes', function (pres, req, res) {
  console.log('RAW Response from the target', JSON.stringify(pres.headers, true, 2));
  pres.on('data', function(chunk) {
    console.log('body: '+chunk);
  });
});

var s5 = http.createServer(function(req, res) {
  logFile.write(JSON.stringify(req.headers, true, 2));
  p5.web(req, res, { target: 'http://127.0.0.1:8006', changeOrigin:true });
});
s5.listen(8005);


http.createServer(function (req, res) {
  res.writeHead(200, { 'Content-Type': 'text/plain' });
  res.write('request successfully proxied!' + '\n' + JSON.stringify(req.headers, true, 2));
  res.end();
}).listen(8006);

var uuid = /[a-z0-9]{26,26}/;
var canon = function(url) {
  var words = url.split('/');
  for (var ii=0; ii < words.length; ii++)
      if (uuid.test(words[ii])) words[ii] = 'xxx';
  return words.join('_');
};
var map = {};

var apiFile = fs.createWriteStream('./api.txt');


var o7 = {
    target: 'http://127.0.0.1:8065',
    changeOrigin: true
};
var ws7 = {
    target: 'ws://127.0.0.1:8065',
    changeOrigin: true
};

var p7 = httpProxy.createProxyServer();
p7.on('proxyReqWs', function(preq) {
    preq.setHeader('origin','http://127.0.0.1:8065');
});
p7.on('proxyRes', function (pres, req, res) {
    var name = canon(req.url);
    if (! req.url.startsWith('/api/')) return;
    var store =  map[name];
    // console.log('RAW Response from the target', JSON.stringify(pres.headers, true, 2));
    if (store !== undefined) {
        map[name] = true;
        var filename = './api/'+name+'_reps';
        var file;
        pres.on('data',function(chunk) {
            if (! file) file  = fs.createWriteStream(filename);
            file.write(chunk);
        });
        pres.on('end',function() {
            if (file) file.close();
        });
    }
});

// var wsfile = fs.createWriteStream('api/ws.txt');

var s7 = http.createServer(function(req, res) {
    var name = canon(req.url);
    var stored = false;
    if (map[name]==undefined) {
        stored = true;
        var filename = './api/'+name+'_reqs';
        map[name] = req;
        var file;
        req.on('data',function(chunk) {
            if (! file) file  = fs.createWriteStream(filename);
            file.write(chunk);
        });
        req.on('end',function() {
            if (file) file.close();
            else apiFile.write(name+'\n');
        });
        console.log('stored:  ' + name + ' ' + stored);
    }
    else console.log('request: '+ name);
    p7.web(req, res, o7);
});
s7.on('upgrade', function (req, socket, head) {
    // console.log('socket:  ' + req.url);
  p7.ws(req, socket, head, ws7);
  // wsfile.write(head);
  // socket.on('data',function(data) {
  //     wsfile.write(data);
  // });
});


s7.listen(8007);

/*
    b = $$('td.data-column');
    for (var ii=0,txt='\n{\n'; ii<b.length; ii++) txt += b[ii].innerText + ',\n\n'; txt += '0\n}\n'

    save as ~/t1.log
    head -n -1 ~/t1.log | tail -n +3 > t1.log
    cp=$(mvnrun org.jsonschema2pojo:jsonschema2pojo-cli)
    java -cp $cp org.jsonschema2pojo.cli.Jsonschema2PojoCLI -s t1.log -t ws -T JSON -a NONE -P -da -E -S
    java -cp $cp org.jsonschema2pojo.cli.Jsonschema2PojoCLI -s apix -t srcx -T JSON -a NONE -P -da -E -S -p mm.rest

for ii in $(ls -rS rest/*); do rm -f *.java; cp $ii .; git add -u; git add *.java; git commit -m tmp; done
for ii in $(git l7); do git show -M1 -U999 --color-words $ii; done > t1

 */



a = {
    uuid: uuid,
    canon: canon,
    p7: p7,
    s7: s7,
    map: map
};
