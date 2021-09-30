console.log("Running main file!");




var matches = 0;

function setupWS(){

    if(ws_sock === null) {
        ws_sock = io();
    }

    ws_sock.on("connect", (e) => {
        console.log("Connected to server web socket");
        console.log(e);

        ws_sock.emit("test_event", "test data");

    });

    ws_sock.on("disconnect", () => { console.log("Disconnect from server web socket"); });

    ws_sock.on("test_event", (e) => {
        console.log("Received test_event from server");
        console.log(e);
    });

    ws_sock.on("cep_match", (match) => {
        console.log("CEP Match: " + match);
        var output = document.getElementById("query-output");
        if(matches < 20) {
            output.innerHTML += '<div>' + prettyPrintMatch(match) + ' - time: ' + match['Composite']['timestamp'] + '</div>';
            matches++;
        }
        ws_sock.emit("test_event", "test_data");
    });
    var discon_btn = document.getElementById("disconnect-ws");
    discon_btn.classList.toggle("btn-danger");
    discon_btn.classList.toggle("btn-default");
    discon_btn.disabled = false;
};

function teardownWS(){
    ws_sock.disconnect();
    ws_sock = null;
    console.log("[INFO] Disconnected web socket");
    var discon_btn = document.getElementById("disconnect-ws");
    discon_btn.classList.toggle("btn-danger");
    discon_btn.classList.toggle("btn-default");
    discon_btn.disabled = true;
};

function connectToQuery(query_id){

}

function submitQuery(){
    console.log("Running submitQuery()");
    var query_textarea = document.getElementById("query");
    var query = query_textarea.value;
    var output = document.getElementById("query-output");
//    output.innerHTML = ""
    sendJS("/query", {"query": query}, (r) => {
        if(r['ok'] == 1){
            console.log("query accepted");
            setupWS();
        } else {
            console.log("query rejected");
        }
    });
}

function getQueryPlan(){
    console.log("Running getQueryPlan()");
    var query_textarea = document.getElementById("query");
    var query = query_textarea.value;
    sendJS("/query/plan", {"query": query}, (r) => {

        if(r['ok']){
            console.log("query plan returned");
            var output = document.getElementById("query-output");
            output.innerHTML = '<div>' + r['plan'] + '</div>';
        } else {
            console.log("error in query plan");
        }
    })
}

function getQueryAST(){
    console.log("Running getQueryAST()");
    var query_textarea = document.getElementById("query");
    var query = query_textarea.value;
    sendJS("/query/ast", {"query": query}, (r) => {

        if(r['ok']){
            console.log("query AST returned");
            var output = document.getElementById("query-output");
            output.innerHTML = '<pre>' + JSON.stringify(r['ast'], null, 2) + '</pre>';
        } else {
            console.log("error in query AST");
        }
    })
}

function loadHistory(){
    var query_out = document.getElementById("query-output");
    console.log("Fetching query history...");
    query_out.innerHTML = "";
    sendJS('/query/history', {}, (r) => {
        for(var i in r['history']){
            query_out.innerHTML += '<div>' + prettyPrintMatch(r['history'][i]['match'], null, 2) + ' - timestamp: ' + r['history'][i]['timestamp'] +' - offset: '+ r['history'][i]['offset'] +' </div>';
        }

    })

}


function attributeList(event_types, name) {
    var name_match = event_types.filter(t => t['name'] == name);
    if(name_match.length > 0) {
        var event_type = name_match[0];
        var fields = event_type['fields']
        var input_list = "";
        for(var i in fields) {
            input_list += '<div class="col form-floating event_field_input">';
            input_list += '<input data-eventtype="' + name + '" placeholder="' + fields[i] + '" class="form-control" id="'+ fields[i] +'" type="text"></input>';
            input_list += '<label class="mx-3" for="' + fields[i] + '">'+ fields[i] + '</label>';
            input_list += '</div>';
        }
        return input_list;
    } else {
        throw new Exception("Cannot find name in event type list");
    }
};

function attributeListArray(event_types, name) {
var name_match = event_types.filter(t => t['name'] == name);
    if(name_match.length > 0) {
        var event_type = name_match[0];
        var fields = event_type['fields']
        var input_list = [];
        for(var i in fields) {
            var aDiv = document.createElement("div");
            aDiv.classList = "col form-floating event_field_input";
            aDiv.innerHTML += '<input data-eventtype="' + name + '" placeholder="' + fields[i] + '" class="form-control" id="'+ fields[i] +'" type="text"></input>';
            aDiv.innerHTML += '<label class="mx-3" for="' + fields[i] + '">'+ fields[i] + '</label>';
            input_list.push(aDiv);
        }
        return input_list;
    } else {
        throw new Exception("Cannot find name in event type list");
    }
};

function setupRemoveEventBtnHandler() {

    $(".remove-event-btn").off("click").on("click", (e) => {
        console.log("running remove event button click handler");
        var to_remove = e.target.parentElement
        var remove_from = e.target.parentElement.parentElement
        remove_from.removeChild(to_remove);

    });
};

var query_ast = null;

function setupSelectClickHandler() {

    $(".event_types").off("change").on("change", (e) => {
        console.log("Running event type click handler");
        var selector = e.target;
        var selected_type = selector.value;
        var row = selector.parentElement.parentElement;
        var field_list = row.querySelectorAll(".event_field_input");
        for(var i = 0; i < field_list.length; i++) {
         row.removeChild(field_list[i]);
        }
        var new_fields = attributeListArray(query_ast['event_types'], selected_type);
        var before_this = row.querySelector(".remove-event-btn");
        console.log("Adding new fields for type " + selected_type);
        new_fields.forEach( field => row.insertBefore(field, before_this) );

    });
};

function addEventRow(ast) {
    var event_list = document.getElementById("event-send-list");
    var event_types = ast['event_types'];
    var event_names = event_types.map(t => t['name']);
    var option_list = "";

    for(var i in event_names) {
        if(i == 0) {
            var option = '<option value="' + event_names[i] + '" default>'+ event_names[i] +'</option>';
            var default_name = event_names[i];
        } else {
            var option = '<option value="' + event_names[i] + '">'+ event_names[i] +'</option>';
        }
        option_list += option;
    }
    var div = document.createElement("div");
    div.classList = "row my-2 row-cols-lg-5 row-cols-md-5 event_row";

    var inside = `
            <div class="col">
                <select name="event_types" class="event_types form-select form-select-md py-3">
                ${option_list}
                </select>
            </div>
            ${attributeList(event_types, default_name)}
            <button class="col btn btn-danger remove-event-btn">Remove</button>
    `;
    div.innerHTML = inside;
    event_list.appendChild(div);
    setupRemoveEventBtnHandler();
    setupSelectClickHandler();
};

function getEventList() {
    var input_data = $(".event_row").map( (ind, row) => {
        return $(row)
        .find("input")
        .map((ind2, in_elem) => {
            let obj = {};
            obj['event_type'] = in_elem.dataset['eventtype'];
            obj["field_name"] = in_elem.placeholder;
            obj["field_value"] = in_elem.value;
            return obj;
        });
    });


    var type_field_list = new Array();
    for( var i = 0; i < input_data.length; i++) {
        var inner_array = input_data[i]
        // maps type names to field values
        var type_to_fields = [];
        for( var j = 0; j < inner_array.length; j++) {
            var input_value = inner_array[j];
            var event_type = input_value['event_type'];
            if(type_to_fields.length == 0) {
                type_to_fields.push(event_type);
                type_to_fields.push(new Array());
            }
            var new_obj = {};
            new_obj['field_name'] = input_value['field_name'];
            new_obj['field_value'] = input_value['field_value'];
            type_to_fields[1].push(new_obj);
        }
        type_field_list.push(type_to_fields);
    }
    return type_field_list;
}


function addEventOnClick(e) {
//    var event_list = e.target.parentElement().children()[0];
    var query_id = e.target.parentElement.dataset['queryid'];
    sendJS('/query/ast', {'query_id': query_id}, (r) => {
        if(r['ok'] == 1) {
            query_ast = r['ast'];
            addEventRow(r['ast']);
        } else {
            console.log("[ERROR] Error getting AST for query: " + r['err']);
        }
    })
};

let qmd = new QueryMatchDisplay("query-output", QUERY_ID, false);

function documentOnload(){
    console.log("Running documentOnload()");
//    var btn = document.getElementById("submit-query");
//    btn.onclick = () => { submitQuery(); };

    var queryPlanBtn = document.getElementById("query-plan-btn");
    queryPlanBtn.onclick = () => { getQueryPlan(); };

    var queryAstBtn = document.getElementById("query-ast-btn");
    queryAstBtn.onclick = () => { getQueryAST(); };

    var addEventBtn = document.getElementById("add-event");
    addEventBtn.onclick = (e) => { addEventOnClick(e); };

//    var con_btn = document.getElementById("connect-ws");
//    con_btn.onclick = () => { teardownWS(); };
//    qmd.connect();

//    loadHistory();
    $("#send-events").off("click").on("click", (e) => {
        sendJS("/query/send_events", {"events": getEventList(), "query_id": QUERY_ID}, (r) => {
            if(r['ok'] == 1){
                console.log("[INFO] Successfully sent events");
            } else {
                console.log("[ERR] failed to send events: " + r['err']);
            }
        })
    })

    $("#connect-ws").off("click").on("click", (e) => {
        qmd.toggleConnection();
    })

}

document.addEventListener("DOMContentLoaded", (e) => {
    console.log("Running document.onload");
    documentOnload();
});


