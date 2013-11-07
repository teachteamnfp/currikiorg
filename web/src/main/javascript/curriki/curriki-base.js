// vim: ts=4:sw=4
/*global Ext */
/*global _ */

Ext.BLANK_IMAGE_URL = '/xwiki/skins/curriki8/extjs/resources/images/default/s.gif';

Ext.Ajax.defaultHeaders = {
	 'Accept': 'application/json'
	,'Content-Type': 'application/json; charset=utf-8'
};
Ext.Ajax.disableCaching=false;
Ext.Ajax.timeout=120000;


if (!('console' in window) || !(console.log) /* || !('firebug' in console) */){
	var names = ["log", "debug", "info", "warn", "error", "assert", "dir",
	             "dirxml", "group", "groupEnd", "time", "timeEnd", "count",
	             "trace", "profile", "profileEnd"];
	window.console = {};
	for (var i = 0; i < names.length; ++i)
		window.console[names[i]] = Ext.emptyFn
}
console.log('initing Curriki');
/*
 * Example of dynamically loading javascript
function initLoader() {
  var script = document.createElement("script");
  script.src = "http://www.google.com/jsapi?key=ABCDEFG&callback=loadMaps";
  script.type = "text/javascript";
  document.getElementsByTagName("head")[0].appendChild(script);
}
*/

Ext.ns('Curriki');
Curriki.console = window.console;
Ext.ns('Curriki.module');

Curriki.requestCount = 0;

Ext.onReady(function(){
	Curriki.loadingCount = 0;
	Curriki.hideLoadingMask = false;
	Curriki.loadingMask = new Ext.LoadMask(Ext.getBody(), {msg:_('loading.loading_msg')});

    Ext.Ajax.on('beforerequest', function(conn, options){
        options.requestCount = Curriki.requestCount++;
        console.log('beforerequest (' + options.requestCount + ")", conn, options);
        // protection
        //if(options.requestCount>10) throw "No more than 10 requests!";
        Curriki.Ajax.beforerequest(conn, options);
	});
    Ext.Ajax.on('requestcomplete', function(conn, response, options){
console.log('requestcomplete (' + options.requestCount + ")", conn, response, options);
		Curriki.Ajax.requestcomplete(conn, response, options);
	});
    Ext.Ajax.on('requestexception', Curriki.notifyException);
});

Curriki.Ajax = {
	'beforerequest': function(conn, options) {
		Curriki.showLoading(options.waitMsg);
	}

	,'requestcomplete': function(conn, response, options) {
		Curriki.hideLoading();
	}

	,'requestexception': function(conn, response, options) {
		Curriki.hideLoading(true);
	}
};


Curriki.notifyException = function(exception){
        console.log('requestexception', exception);
		Curriki.Ajax.requestexception(null, null, null);
        Curriki.logView('/features/ajax/error/');
        var task = new Ext.util.DelayedTask(function(){
            if(!Ext.isEmpty(Curriki.loadingMask)) {
                Curriki.loadingMask.hide();
                Curriki.loadingMask.disable();
            }
            Ext.MessageBox.alert(_("search.connection.error.title"),
                    _("search.connection.error.body"));
        });
        task.delay(100);
	};

Curriki.id = function(prefix){
	return Ext.id('', prefix+':');
};

Curriki.showLoading = function(msg, multi){
	if (multi === true) {
		Curriki.loadingCount++;
	}
	if (!Curriki.hideLoadingMask && !Ext.isEmpty(Curriki.loadingMask)){
		msg = msg||'loading.loading_msg';
		Curriki.loadingMask.msg = _(msg);
		Curriki.loadingMask.enable();
		Curriki.loadingMask.show();
	}
}

Curriki.isISO8601DateParsing = function() {
    if(typeof(Curriki.ISO8601DateParsing)!="undefined") return Curriki.ISO8601DateParsing;
    var s = navigator.userAgent;
    Curriki.ISO8601DateParsing = s.indexOf("OS 5")!=-1 && ( s.indexOf("iPhone")!=-1 || s.indexOf("iPod")!=-1 || s.indexOf("iPad")!=-1);
    console.log("Set ISO8601 parsing to " + Curriki.ISO8601DateParsing);
    return Curriki.ISO8601DateParsing;
}

Curriki.hideLoading = function(multi){
	if (multi === true) {
		Curriki.loadingCount--;
	}
	if (Curriki.loadingCount == 0 && !Ext.isEmpty(Curriki.loadingMask)){
		Curriki.loadingMask.hide();
		Curriki.loadingMask.disable();
	} else if (Curriki.loadingCount < 0) {
		Curriki.loadingCount = 0;
	}
}

Curriki.start = function(callback){
console.log('Start Callback: ', callback);
	var args = {};

	if ("object" === typeof callback){
		if (callback.args){
			args = callback.args;
		}
		if (callback.callback){
			callback = callback.callback;
		} else if (callback.module){
			callback = callback.module;
		}
	}

	if ("string" === typeof callback){
		var module = eval('(Curriki.module.'+callback.toLowerCase()+')');

		if (module && "function" === typeof module.init){
			// callback is the name of a module
			module.init(args);
			if ("function" === typeof module.start) {
				callback = module.start;
			} else {
				callback = Ext.emptyFn;
			}
		} else {
			// callback is a known string
			switch(callback){
				default:
					callback = Ext.emptyFn;
					break;
			}
		}
	}

	if ("function" === typeof callback) {
		callback(args);
	}
};

Curriki.init = function(callback){
console.log('Curriki.init: ', callback);
	if (Ext.isEmpty(Curriki.initialized)) {
		Curriki.data.user.GetUserinfo(function(){Curriki.start(callback);});
		Curriki.initialized = true;
	} else {
		Curriki.start(callback);
	}
};
