// https://github.com/nodejitsu/node-http-proxy
// chrome://inspect


var http = require('http'),
    https = require('https'),
    httpProxy = require('http-proxy');


var args = process.argv.slice(2);
var port = args.length ? args[0] : 8065;
var port2 = args.length > 1 ? args[1] : port;
var port3 = args.length > 2 ? args[2] : 8008;


var o7 = {
    target: 'http://127.0.0.1:'+port,
    changeOrigin: true
};
var o2 = {
    target: 'http://127.0.0.1:'+port2,
    changeOrigin: true
};
var ws7 = {
    target: 'ws://127.0.0.1:'+port2,
    changeOrigin: true
};

var p7 = httpProxy.createProxyServer();
p7.on('proxyReqWs', function(preq) {
    preq.setHeader('origin','http://127.0.0.1:'+port2);
});
p7.on('error', function(err,req,res) {
  return res.end();
  res.writeHead(500, {
    'Content-Type': 'text/plain'
  });
  res.end('Something went wrong. And we are reporting a custom error message.');
});

var s7 = http.createServer(function(req, res) {
    p7.web(req, res, o7);
});
s7.on('upgrade', function (req, socket, head) {
    p7.ws(req, socket, head, ws7);
});

s7.listen(port3);

