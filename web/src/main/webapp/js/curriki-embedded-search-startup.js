function postMessageHandler(c){var b=c.data;var a=b.substr(0,b.indexOf(":"));var d=b.substr(b.indexOf(":")+1);
switch(a){case"resize":console.log("embedded search: recieved resize event");resizeCurrikiIframe(d);
break}}function resizeCurrikiIframe(a){document.getElementById("curriki_search_frame").setAttribute("style",a)
}function setCurrikiIFrameSrc(){var c="/xwiki/bin/view/EmbeddedSearch/AdvancedSearchFrame?xpage=plain";
var b=document.getElementById("curriki_search_frame");var a=CURRIKI_HOST+c+"&embeddingPartnerUrl="+PARTNER_HOST+"&resourceDisplay="+RESOURCE_DISPLAYER+"&embedViewMode="+EMBED_VIEW_MODE;
b.setAttribute("src",a)}if(typeof window.attachEvent==="function"||typeof window.attachEvent==="object"){console.log("search: attached Listener to evenet via window.attachEvent");
window.attachEvent("onmessage",postMessageHandler)}else{if(typeof window.addEventListener==="function"){console.log("search: attached Listener to evenet via window.addEvenListener");
window.addEventListener("message",postMessageHandler,false)}else{if(typeof document.attachEvent==="function"){console.log("search: cors iframe communication is not possible");
document.attachEvent("onmessage",postMessageHandler)}else{console.log("Frame communication not possible")
}}};