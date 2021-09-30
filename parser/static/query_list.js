
function deleteBtnOnclick(e) {
    console.log("Running deleteBtnOnclick...");
    console.log(e);
//    var query_id = e.parent.parent.attrs['query_id'];
    var parent = e.target.parentElement.parentElement;
    var query_id = parent.dataset['queryid']
    var query_string = document.getElementById(query_id).innerText.trim()
    console.log(query_string);
    sendJS('/query/delete', {'query': query_string}, (r) => {
        if(r['ok'] == 1) {
            console.log("Successfully deleted query");
            parent.remove();
        } else {
            console.log("Unsuccessful in query deletion attempt");
        }

    })
}

function documentOnload(){

    console.log("Running documentOnload()");

    var delete_btns =  document.querySelectorAll('.delete-query');

    for(var i in delete_btns) {
        delete_btns[i].onclick = (e) => { deleteBtnOnclick(e); }
    }
}

document.addEventListener("DOMContentLoaded", (e) => {
    console.log("Running document.onload");
    documentOnload();
});