var wsUri = "ws://localhost:8080/http4s/boxes";
var output;

function init() {
    output = document.getElementById("output");
    testWebSocket();
}

function clickSend() {
    var typed = document.getElementById("txtData").value
    doSend(typed);
    writeToScreen("SENDING: " + typed);
}

function testWebSocket() {
    websocket = new WebSocket(wsUri);
    websocket.onopen = onOpen;
    websocket.onclose = onClose;
    websocket.onmessage = onMessage;
    websocket.onerror = onError;
}

function onOpen(evt) {
    writeToScreen("CONNECTED");
    doSend("WebSocket rocks");
}
function onClose(evt) {
    writeToScreen("DISCONNECTED");
}
function onMessage(evt) {
    writeToScreen('<span style="color: blue;">RESPONSE: ' + evt.data+'</span>');
//    websocket.close();
}
function onError(evt) {
    writeToScreen('<span style="color: red;">ERROR:</span> ' + evt.data);
}
function doSend(message) {
    writeToScreen("SENT: " + message);
    websocket.send(message);
}
function writeToScreen(message) {
    var pre = document.createElement("p");
    pre.style.wordWrap = "break-word";
    pre.innerHTML = message;
    output.appendChild(pre);
}
window.addEventListener("load", init, false);