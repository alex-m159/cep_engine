async function sendJS(endpoint, data, callback){
    const headers = {
        "content-type":"application/json",
        "content-length": JSON.stringify(data).length
    };
    var options = {
        method: "POST",
        body: JSON.stringify(data),
        headers: new Headers(headers)
    };

    const resp = await fetch(endpoint, options)
        .then(response => response.json())
        .then(data => {
            callback(data);
        })
        .catch((error) => {
            console.log("{ERROR]:" + error);
        });
};

function prettyPrintMatch(match_json){
    var parsed;
    if(typeof match_json == "string") {
        parsed = JSON.parse(match_json);
    } else {
        parsed = match_json;
    }
    let events = parsed['Composite']["events"];
    var result = "";
    for( var k in events) {
        let ev = events[k];
        result += `${ev['event_type']} ${ev['bind_name']} (`;
        let fields = ev['fields'];
        for(var i in fields){
            if(i < fields.length-1)
                result += `${fields[i]['field_name']} = ${fields[i]['field_value']}, `;
            else
                result += `${fields[i]['field_name']} = ${fields[i]['field_value']}`;
        }
        if(k < events.length -1)
            result += `), `;
        else
            result += `)`;
    }
    return result;
};

/**
    The controller for the query match display planel that's under the query text
    box. Will start a web socket to receive events from the server and add them to the
    display. The display is limited to showing only the last 100
*/
class QueryMatchDisplay {

    constructor(div_id, query_id = 0, get_history = false) {
        this.div_id = div_id;
        this.matches = [];
        this.max_matches = 100;
        this.initialized = false;
        this.get_history = get_history;
        this.query_id = query_id;
        this.is_connected = false;
    }

    init() {
        this.output = document.getElementById(this.div_id);
        this.discon_btn = document.getElementById("connect-ws");
        this.initialized = true;
    }

    setQueryId(query_id) {
        this.query_id = query_id;
    }

    isConnected() {
        return this.is_connected;
    }

    connect() {
        if(!this.initialized) {
            this.init();
        }
        this.ws = io();
        this.ws.on("connect", (e) => {this.onConnect(e);});
        this.ws.on("disconnect", (e) => { this.onDisconnect(e);});
        this.ws.on("test_event", (e) => { this.onTestEvent(e);});
        this.ws.on("cep_match", (e) => { this.onCepMatch(e);});

R
        if(!this.discon_btn.classList.contains("btn-danger")) {
            this.discon_btn.classList.add("btn-danger")
        }
        if(this.discon_btn.classList.contains("btn-default")) {
            this.discon_btn.classList.remove("btn-default");
        }
        this.discon_btn.innerText = "Disconnect from Stream";
        this.discon_btn.disabled = false;
        if(this.get_history){
            this.getHistory();
        }
        this.is_connected = true;

    }

    getHistory() {
        console.log("[INFO] Sent history event");
        this.ws.emit("history", "doesn't matter");
    }

    onConnect(e) {
        console.log("Connected to server web socket");
        console.log(e);
        this.ws.emit("test_event", {"query_id": this.query_id, "other": "data"});
    }

    onDisconnect(e) {
        console.log("Disconnect from server web socket");
    }

    onTestEvent(e) {
        console.log("Received test_event from server");
        console.log(e);
    }

    handleMatch(match) {
        this.match_count += 1;
        this.matches.push(match);
        if(this.matches.length > this.max_matches) {
            this.matches = this.matches.slice(1)
        }

        this.renderMatches();
    }

    renderMatches() {
        this.output.innerHTML = "";
        for(var i in this.matches) {
            this.output.innerHTML += '<div>' + prettyPrintMatch(this.matches[i]) + ' - time: ' + this.matches[i]['Composite']['timestamp'] + ' - offset: ' + this.matches[i]['offset'] + '</div>';
        }
    }

    onCepMatch(matches) {
        console.log("CEP Match: " + matches["data"]);
        for(var i in matches['data']) {
            var m = JSON.parse(matches['data'][i]['value']);
            m['offset'] = matches['data'][i]['offset'];
            this.handleMatch(m);
        }
    }

    disconnect() {
        this.ws.disconnect();
        console.log("[INFO] Disconnected web socket");
        if(this.discon_btn.classList.contains("btn-danger")) {
            this.discon_btn.classList.remove("btn-danger")
        }
        if(!this.discon_btn.classList.contains("btn-default")) {
            this.discon_btn.classList.add("btn-default");
        }
        this.discon_btn.innerText = "Connect to Stream";
        this.is_connected = false;

    }

    toggleConnection() {
        if(this.isConnected()) {
            this.disconnect();
        } else {
            this.connect();
        }
    }

}

