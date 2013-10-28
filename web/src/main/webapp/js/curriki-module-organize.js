(function(){Ext.ns("Curriki.module.organize");Ext.ns("Curriki.data.organize");var a=Curriki.module.organize;
var b=Curriki.data.organize;var c=Curriki.ui;a.init=function(){b.removed=[];b.moved=[];
b.changedFolders=[];b.selected=null;b.confirmedCallback=Ext.emptyFn;c.treeLoader.Organize=function(d){c.treeLoader.Organize.superclass.constructor.call(this)
};Ext.extend(c.treeLoader.Organize,c.treeLoader.Base,{setFullRollover:true,setAllowDrag:true,setUniqueId:true,disableUnviewable:false,hideInvalid:true,unviewableText:_("organize.dialog.resource.unavailable.indicator_node"),unviewableQtip:_("organize.dialog.resource.unavailable.indicator_rollover")});
a.logCancelled=function(){var d=b.resource.replace(".","/");Curriki.logView("/features/resources/organize/"+d+"/cancelled")
};a.logCompleted=function(){var d=b.resource.replace(".","/");Curriki.logView("/features/resources/organize/"+d+"/completed")
};a.getMovedList=function(d){if("undefined"!=typeof d.attributes.origLocation){b.moved.push(d)
}if(d.hasChildNodes()){d.eachChild(a.getMovedList)}};a.mainDlg=Ext.extend(c.dialog.Messages,{initComponent:function(){Ext.apply(this,{id:"OrganizeDialogueWindow",title:_("organize.dialog_header"),cls:"organize resource resource-edit",autoScroll:false,width:634,bbar:[{text:_("organize.dialog.remove_button"),id:"organize-remove-btn",cls:"button btn-remove",disabled:true,listeners:{click:function(d,f){if(b.selected!==null){b.removed.push(b.selected.remove());
d.disable()}}}},"->",{text:_("organize.dialog.cancel_button"),id:"organize-cancel-btn",cls:"button btn-cancel",listeners:{click:{fn:function(d,f){a.logCancelled();
this.close();if(Ext.isIE){window.location.reload()}},scope:this}}},{text:_("organize.dialog.done_button"),id:"organize-done-btn",cls:"button btn-done",disabled:true,listeners:{click:{fn:function(g,i){var h=[];
console.log("Start Organizing");Ext.getCmp("organize-tree-cmp").getRootNode().eachChild(a.getMovedList);
b.changedFolders=b.moved.concat(b.removed).collect(function(e){return e.attributes.origLocation.parentNode
}).concat(b.moved.collect(function(e){return e.parentNode})).uniq();var f=function(){var e=function(){a.logCompleted();
Curriki.hideLoading(true);Ext.getCmp("OrganizeDialogueWindow").close();window.location.reload()
};b.changedFolders.each(function(k){var j=e;e=function(){if(k.attributes.rights.edit){var l=k.childNodes.collect(function(o){return o.attributes.pageName
});var n="";console.log("saving folder",k.attributes.pageName,l,k);var m=[];if("undefined"!=typeof k.attributes.addedNodes){k.attributes.addedNodes.uniq().each(function(o){m.push(o)
})}if("undefined"!=typeof k.attributes.removedNodes){k.attributes.removedNodes.uniq().each(function(o){if(m.indexOf(o)==-1){n+=_("organize.history.removed_note",o.attributes.pageName,o.attributes.order+1)+" "
}else{m.remove(o)}})}if("undefined"!=typeof k.attributes.addedNodes){m.each(function(o){n+=_("organize.history.inserted_note",o.attributes.pageName,l.indexOf(o.attributes.pageName)+1)+" "
})}console.log("logging",n);Curriki.assets.SetSubassets(k.attributes.pageName,null,l,n,function(p){if("function"==typeof j){j()
}})}else{if("function"==typeof j){j()}}}});e()};b.changedFolders.each(function(j){var e=f;
f=function(){Curriki.assets.GetMetadata(j.attributes.assetpage,function(k){if(k.revision!=j.attributes.revision){Curriki.hideLoading(true);
alert(_("organize.error.concurrency_text",[k.title,"/xwiki/bin/view/"+k.assetpage.replace(".","/")]));
this.close();Ext.getCmp("OrganizeDialogueWindow").close();a.start(b.startInfo)}else{if("function"==typeof e){e()
}}})}});var d=f;f=function(){Curriki.showLoading(null,true);d()};b.confirmedCallback=f;
b.confirmMsg="";b.removed.each(function(e){b.confirmMsg+="<br />"+_("organize.confirmation.dialog_removed_listing",e.text,e.attributes.origLocation.parentNode.text)
});b.moved.uniq().each(function(e){if(b.removed.indexOf(e)==-1){b.confirmMsg+="<br />"+_("organize.confirmation.dialog_moved_listing",e.text,e.attributes.origLocation.index,e.attributes.origLocation.parentNode.text,e.parentNode.indexOf(e)+1,e.parentNode.text)
}});c.show("confirmOrganizeDlg")},scope:this}}}],items:[{xtype:"panel",id:"guidingquestion-container",cls:"guidingquestion-container",items:[{xtype:"box",autoEl:{tag:"div",html:_("organize.dialog.guidingquestion_text"),cls:"guidingquestion"}},{xtype:"box",autoEl:{tag:"div",html:_("organize.dialog.instruction_text"),cls:"instruction"}}]},{xtype:"panel",id:"organize-panel",cls:"organize-panel",items:[{xtype:"treepanel",loader:new c.treeLoader.Organize(),id:"organize-tree-cmp",autoScroll:true,maxHeight:390,useArrows:true,border:false,hlColor:"93C53C",hlDrop:false,cls:"organize-tree",animate:true,enableDD:true,ddScroll:true,containerScroll:true,rootVisible:true,listeners:{render:function(d){console.log("set up selectionchange",d);
d.getSelectionModel().on("selectionchange",function(e,f){console.log("selection change",f,e);
b.selected=f;if((f!=null)&&(f!=d.getRootNode())&&f.parentNode.attributes.rights.edit){Ext.getCmp("organize-remove-btn").enable()
}else{Ext.getCmp("organize-remove-btn").disable()}})},nodedragover:function(d){var e=d.dropNode;
var f=d.target;if(d.point!=="append"){f=f.parentNode}if(e.parentNode!=f){if(f.findChild("pageName",e.attributes.pageName)!=null){console.log("dragover - no",d,e,f);
d.cancel=true;return false}console.log("dragover - okay",d,e,f)}},expandnode:{fn:function(e){console.log("expandnode "+this);
var d=this.findById("organize-tree-cmp");if(!Ext.isEmpty(d)){d.fireEvent("afterlayout",d,d.getLayout())
}console.log("expandnode done")},scope:this},afterlayout:function(e,d){console.log("afterlayout 1");
if(this.afterlayout_maxheight){}else{if(e.getBox().height>e.maxHeight){e.setHeight(e.maxHeight);
e.findParentByType("organizeDlg").center();this.afterlayout_maxheight=true}else{e.setHeight("auto")
}}},movenode:function(d,h,f,g,e){console.log("moved node",h,f,g,e,d);if("undefined"===typeof g.attributes.addedNodes){g.attributes.addedNodes=[]
}g.attributes.addedNodes.push(h)},beforeremove:function(d,f,g){console.log("before remove node",g,f,d);
if("undefined"==typeof g.attributes.origLocation){var e=f.indexOf(g)+1;g.attributes.origLocation={parentResource:f.pageName,index:e,parentNode:f}
}},remove:function(e,g,h){console.log("removed node",h,g,e);if(g.attributes.rights.edit){if("undefined"!=typeof g.attributes.addedNodes){g.attributes.addedNodes.remove(h)
}if("undefined"===typeof h.attributes.origLocation.parentNode.attributes.removedNodes){h.attributes.origLocation.parentNode.attributes.removedNodes=[]
}h.attributes.origLocation.parentNode.attributes.removedNodes.push(h)}else{var d=h.attributes;
var i={assetpage:d.assetpage,assetType:d.assetType,category:d.category,creator:d.creator,creatorName:d.creatorName,description:d.description,displayTitle:d.displayTitle,educational_level:d.educational_level,externalRightsHolder:d.externalRightsHolder,fcnodes:d.fcnodes,fcreviewer:d.fcreviewer,fcstatus:d.fcstatus,fullAssetType:d.fullAssetType,fwItems:d.fwItems,fw_items:d.fw_items,ict:d.ict,instructional_component:d.instructional_component,keywords:d.keywords,language:d.language,levels:d.levels,licenseType:d.licenseType,qtip:d.qtip,revision:d.revision,rights:d.rights,rightsHolder:d.rightsHolder,rightsList:d.rightsList,subcategory:d.subcategory,text:d.text,title:d.title,tracking:d.tracking,leaf:d.leaf,allowDrag:d.allowDrag,allowDrop:d.allowDrop,expanded:false};
var j=new c.treeLoader.Organize();var f=j.createNode(i);g.insertBefore(f,g.item(d.origLocation.index-1))
}Ext.getCmp("organize-done-btn").enable()}},root:b.root}]}]});a.mainDlg.superclass.initComponent.call(this)
}});Ext.reg("organizeDlg",a.mainDlg);a.confirmDlg=Ext.extend(c.dialog.Messages,{initComponent:function(){Ext.apply(this,{id:"ConfirmOrganizeDialogueWindow",title:_("organize.confirmation.dialog_header"),cls:"organize resource resource-edit",autoScroll:false,listeners:{render:{fn:function(d){Ext.get("organize-confirm-cancel-button").focus()
},scope:this}},bbar:["->",{text:_("organize.dialog.cancel_button"),id:"organize-confirm-cancel-button",cls:"button btn-cancel",listeners:{click:{fn:function(f,d){a.logCancelled();
this.close()},scope:this}}},{text:_("organize.dialog.ok_button"),id:"organize-confirm-ok-button",cls:"button btn-next",listeners:{click:{fn:function(f,d){b.confirmedCallback();
this.close()},scope:this}}}],items:[{xtype:"box",autoEl:{tag:"div",html:_("organize.confirmation.dialog_summary_text")+"<br />"+b.confirmMsg+(b.removed.size()>0?("<br /><br />"+_("organize.confirmation.dialog_note_text")):"")+"<br /><br />"+_("organize.confirmation.dialog_instruction_text")}}]});
a.confirmDlg.superclass.initComponent.call(this)}});Ext.reg("confirmOrganizeDlg",a.confirmDlg);
a.intentionDlg=Ext.extend(c.dialog.Messages,{initComponent:function(){Ext.apply(this,{id:"IntentionOrganizeDialogueWindow",title:_("organize.intention.dialog_title"),cls:"organize resource resource-edit",autoScroll:false,listeners:{render:{fn:function(d){Ext.get("organize-intention-continue-button").focus()
},scope:this}},bbar:[{text:_("organize.intention.message.continue_button"),id:"organize-intention-continue-button",cls:"button continue-btn",listeners:{click:{fn:function(f,d){c.show("organizeDlg");
this.close()},scope:this}}},{text:_("organize.dialog.cancel_button"),id:"organize-intention-cancel-button",cls:"button cancel-btn",listeners:{click:{fn:function(f,d){a.logCancelled();
this.close()},scope:this}}}],items:[{xtype:"box",autoEl:{tag:"div",html:_("organize.intention.message_text",b.title,b.creatorName)}}]});
a.intentionDlg.superclass.initComponent.call(this)}});Ext.reg("intentOrganizeDlg",a.intentionDlg);
a.display=function(){if(b.creator==Curriki.global.username||Curriki.global.isAdmin){c.show("organizeDlg")
}else{c.show("intentOrganizeDlg")}};a.startMetadata=function(d){Curriki.assets.GetMetadata(d,function(e){console.log("returned",e);
e.fwItems=e.fw_items;e.levels=e.educational_level;e.ict=e.instructional_component;
e.displayTitle=e.title;e.rights=e.rightsList;e.leaf=false;e.allowDrag=false;e.allowDrop=true;
e.expanded=true;var f=new c.treeLoader.Organize();b.root=f.createNode(e);console.log("created",b.root);
b.root.addListener("beforecollapse",function(){return false});b.root.addListener("beforechildrenrendered",function(g){if("undefined"==typeof b.initialFocus){Ext.Element.fly(g.getUI().getAnchor()).focus();
b.initialFocus=true}});b.resource=d;Ext.onReady(function(){a.display()})})};return true
};a.start=function(d){if(a.init()){if("undefined"==typeof d||"undefined"==typeof d.assetPage){alert("No resource to organize given.");
return false}b.resource=d.assetPage;if("undefined"==typeof d.creator||((d.creator!=Curriki.global.username&&!Curriki.global.isAdmin)&&("undefined"==typeof d.title||"undefined"==typeof d.creatorName))){Curriki.assets.GetAssetInfo(b.resource,function(e){a.start(e)
})}else{b.startInfo=d;b.creator=d.creator;b.title=d.title||"No Title Given";b.creatorName=d.creatorName||"No Username Given";
a.startMetadata(b.resource)}}else{alert("ERROR: Could not start Organize.");return false
}}})();