Ext.ns("Curriki.module.nominate");Curriki.module.nominate.init=function(){if(Ext.isEmpty(Curriki.module.nominate.initialized)){var a=Curriki.module.nominate;a.ie_size_shift=10;a.EnableNext=function(){Ext.getCmp("nextbutton").enable()};a.DisableNext=function(){Ext.getCmp("nextbutton").disable()};a.NominateDialogueId="nominate-resource-dialogue";a.NominateResource=Ext.extend(Curriki.ui.dialog.Actions,{initComponent:function(){Ext.apply(this,{title:_("curriki.crs.nominate"),cls:"Caption",id:a.NominateDialogueId,items:[{xtype:"panel",items:[{xtype:"box",cls:"crs_nominate_title",autoEl:{tag:"div",html:_("curriki.crs.nominate.nominatefollowingresourceforreview"),cls:"crs_nominate_title"}},{xtype:"box",autoEl:{tag:"div",html:Curriki.current.assetTitle,cls:"crs_nominate_pagename"}}]},{xtype:"form",id:"nominateResourcePanel",formId:"nominateResourceForm",cls:"form-container",labelWidth:25,autoScroll:true,border:false,defaults:{labelSeparator:"",hideLabel:true,name:"nominateResource"},bbar:["->",{text:_("curriki.crs.nominate.cancel"),id:"cancelbutton",cls:"button cancel",listeners:{click:{fn:function(){this.close();window.location.href=Curriki.current.cameFrom},scope:this}}},{text:_("curriki.crs.nominate.submit"),id:"submitbutton",cls:"submitbutton button next",listeners:{click:{fn:function(){var b=this.findByType("form")[0].getForm();if(b.isValid()){var c=(b.getValues(false))["nominate-comments"];a.Nominate(c)}else{alert("Invalid Form")}},scope:this}}}],monitorValid:true,listeners:{render:function(b){b.ownerCt.on("bodyresize",function(d,e,c){if(c==="auto"){b.setHeight("auto")}else{b.setHeight(d.getInnerHeight()-(d.findByType("panel")[0].getBox().height+(Ext.isIE?a.ie_size_shift:0)))}})}},items:[{xtype:"box",autoEl:{tag:"div",html:_("curriki.crs.nominate.comments"),cls:"crs_nominate_title"}},{xtype:"box",autoEl:{tag:"div",html:_("curriki.crs.nominate.commentstext"),cls:"crs_nominate_commentstext"}},{xtype:"textarea",id:"nominate-comments",name:"nominate-comments",width:"80%"},{xtype:"box",autoEl:{tag:"div",html:_("curriki.crs.nominate.commentsfootertext"),cls:"crs_nominate_commentstext"}}]}]});a.NominateResource.superclass.initComponent.call(this)}});Ext.reg("nominateResourceDialog",a.NominateResource);a.Nominate=function(b){Curriki.assets.NominateAsset(Curriki.current.assetName,b,function(c){window.location.href=Curriki.current.cameFrom})};Curriki.module.nominate.initialized=true}};Curriki.module.nominate.nominateResource=function(a){Curriki.module.nominate.initAndStart(function(){Curriki.ui.show("nominateResourceDialog")},a)};Curriki.module.nominate.initAndStart=function(c,a){var b=Curriki.current;if(!Ext.isEmpty(a)){b.assetName=a.assetName||b.assetName;b.parentAsset=a.parentAsset||b.parentAsset;b.publishSpace=a.publishSpace||b.publishSpace;b.cameFrom=window.location.href;b.assetTitle=a.assetTitle||b.assetTitle;b.assetType=a.assetType||b.assetType;b.parentTitle=a.parentTitle||b.parentTitle}Curriki.init(function(){if(Ext.isEmpty(Curriki.data.user.me)||"XWiki.XWikiGuest"===Curriki.data.user.me.username){window.location.href="/xwiki/bin/login/XWiki/XWikiLogin?xredirect="+window.location.href;return}Curriki.module.nominate.init();var f=function(){c()};var d;if(!Ext.isEmpty(b.parentAsset)&&Ext.isEmpty(b.parentTitle)){d=function(){Curriki.assets.GetAssetInfo(b.parentAsset,function(g){Curriki.current.parentTitle=g.title;f()})}}else{d=function(){f()}}var e;if(!Ext.isEmpty(b.assetName)&&(Ext.isEmpty(b.assetTitle)||Ext.isEmpty(b.assetType))){e=function(){Curriki.assets.GetAssetInfo(b.assetName,function(g){Curriki.current.assetTitle=g.title;Curriki.current.assetType=g.assetType;d()})}}else{e=function(){d()}}e()})};Curriki.module.nominate.loaded=true;