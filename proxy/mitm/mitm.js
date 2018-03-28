

var http = require('http'),
    https = require('https'),
    httpProxy = require('http-proxy');




var fs = require('fs');





var uuid = /[a-z0-9]{26,26}/;
var canon = function(url,meth) {
  var words = url.split('/');
  for (var ii=0; ii < words.length; ii++)
      if (uuid.test(words[ii])) words[ii] = 'xxx';
  return words.join('_')+'_'+meth;
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

var details = true;

var p7 = httpProxy.createProxyServer();
p7.on('proxyReqWs', function(preq) {
    preq.setHeader('origin','http://127.0.0.1:8065');
});
p7.on('proxyRes', function (pres, req, res) {
    var name = canon(req.url,req.method);
    if (! req.url.startsWith('/api/')) return;
    if (true)
	return;
    var store =  map[name];
    // console.log('RAW Response from the target', JSON.stringify(pres.headers, true, 2));
    if (store !== undefined) {
        map[name] = true;
        var filename = './api/'+name+'_reps';
	if (details)
	    fs.writeFile(filename+'_header',JSON.stringify(pres.headers,null,4),()=>{});
	var chunks = [];
        pres.on('data',function(chunk) {
	    chunks.push(chunk);
        });
        pres.on('end',function() {
	    var buf = Buffer.concat(chunks);
	    if (buf.length) fs.writeFile(filename,buf,()=>{});
        });
    }
});

// var wsfile = fs.createWriteStream('api/ws.txt');

var s7 = http.createServer(function(req, res) {
    var name = canon(req.url,req.method);
    var api = req.url.startsWith('/api/');
    if (api) {
        var filename = './api/'+name+'_reqs';

	if (details) {
	var soc = req.connection;
	if (api) {
	    if (details)
		fs.writeFile(filename+'_header',JSON.stringify(req.headers,null,4),()=>{});

	    var saver = function(chunk) {
		if (details)
		    fs.writeFile(filename+'_raw',chunk,()=>{});
	    };
	    if (soc.__srl) { saver(soc.__srl); delete soc.__srl; }
	    else soc.__srl = saver;
	}
	else soc.__srl = null;
	}
	
	var chunks = [];
        req.on('data',function(chunk) {
	    chunks.push(chunk);
        });
        req.on('end',function() {
	    var buf = Buffer.concat(chunks);
	    if (map[name]===undefined) map[name] = [];
	    if (buf.length) {
		try {
		    var obj = JSON.parse(buf);
		    map[name].push(obj);
		}
		catch (ex) {
		    if (map[name].length==0)
			map[name].push(buf);
		}
	    }
        });
        console.log('stored:  ' + name);
    }
    else if (details) {
	console.log('request: '+ name);
	req.connection.__srl = null;
    }
    p7.web(req, res, o7);
});
s7.on('upgrade', function (req, socket, head) {
  p7.ws(req, socket, head, ws7);
});

if (details)
s7.on('connection', function(socket) {
    socket.once('data', function(chunk) {
	var val = socket.__srl;
	if (val) { val(chunk); delete socket.__srl; }
	else if (val===undefined) socket.__srl = chunk;
    });
});

s7.listen(8007);

var stdin = process.openStdin();
stdin.on('data', function(chunk) {
    for (var name in map) {
        var filename = './api/'+name+'_reqs';
	var text = JSON.stringify(map[name]);
	if (map[name].length)
	    fs.writeFile(filename,text,()=>{});
	else
	    apiFile.write(name+'\n');
    }
    console.log("files saved");
});
