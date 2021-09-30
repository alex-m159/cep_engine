console.log("Running main file!");

//var ws_sock = null;

var match_count = 0;



let qmd = new QueryMatchDisplay("query-output");

function submitQuery(){
    console.log("Running submitQuery()");
    var query_textarea = document.getElementById("query");
    var query = query_textarea.value;
    var output = document.getElementById("query-output");
//    output.innerHTML = ""
    sendJS("/query", {"query": query}, (r) => {
        if(r['ok'] == 1){
            console.log("query accepted");
            qmd.setQueryId(r['query_id']);
            qmd.connect();
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

function documentOnload(){
    console.log("Running documentOnload()");
    var btn = document.getElementById("submit-query");
    btn.onclick = () => { submitQuery(); };

    var queryPlanBtn = document.getElementById("query-plan-btn");
    queryPlanBtn.onclick = () => { getQueryPlan(); };

    var queryAstBtn = document.getElementById("query-ast-btn");
    queryAstBtn.onclick = () => { getQueryAST(); };


}

document.addEventListener("DOMContentLoaded", (e) => {
    console.log("Running document.onload");
    documentOnload();
});


