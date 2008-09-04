// vim: ts=4:sw=4
/*global Ext */
/*global Curriki */
/*global _ */

(function(){
var modName = 'group';

Ext.ns('Curriki.module.search.form.'+modName);

var Search = Curriki.module.search;

var form = Search.form[modName];
var data = Search.data[modName];

form.init = function(){
	console.log('form.group: init');

	var comboWidth = 160;
	var comboListWidth = comboWidth+17;

	form.termPanel = Search.util.createTermPanel(modName, form);
//	form.helpPanel = Search.util.createHelpPanel(modName, form);

	form.filterPanel = {
		xtype:'form'
		,labelAlign:'top'
		,id:'search-filterPanel-'+modName
		,formId:'search-filterForm-'+modName
		,border:false
		,items:[
			form.termPanel
//			,form.helpPanel
			,{
				xtype:'fieldset'
				,title:_('search.advanced.search.button')
				,autoHeight:true
				,collapsible:true
				,collapsed:true
				,border:true
				,stateful:true
				,stateEvents:['expand','collapse']
				,listeners:{
					'statesave':{
						fn:Search.util.fieldsetPanelSave
					}
					,'staterestore':{
						fn:Search.util.fieldsetPanelRestore
					}
				}
				,items:[{
					layout:'column'
					,border:false
					,defaults:{
						border:false
						,hideLabel:true
					}
					,items:[{
						columnWidth:0.33
						,layout:'form'
						,defaults:{
							hideLabel:true
						}
						,items:[{
							xtype:'combo'
							,fieldLabel:'Subject'
							,id:'combo-subject-'+modName
							,hiddenName:'subjectparent'
							,width:comboWidth
							,listWidth:comboListWidth
							,mode:'local'
							,store:data.filter.store.subject
							,displayField:'subject'
							,valueField:'id'
							,typeAhead:true
							,triggerAction:'all'
							,emptyText:_('XWiki.CurrikiSpaceClass_topic_FW_masterFramework.WebHome.UNSPECIFIED')
							,selectOnFocus:true
							,forceSelection:true
							,listeners:{
								select:{
									fn:function(combo, value){
										var subSubject = Ext.getCmp('combo-subsubject-'+modName);
										if (combo.getValue() === '') {
											subSubject.clearValue();
											subSubject.hide();
										} else {
											subSubject.show();
											subSubject.clearValue();
											subSubject.store.filter('parentItem', combo.getValue());
											subSubject.setValue(combo.getValue());
										}
									}
								}
							}
						},{
							xtype:'combo'
							,fieldLabel:'Sub Subject'
							,id:'combo-subsubject-'+modName
							,hiddenName:'subject'
							,width:comboWidth
							,listWidth:comboListWidth
							,mode:'local'
							,store:data.filter.store.subsubject
							,displayField:'subject'
							,valueField:'id'
							,typeAhead:true
							,triggerAction:'all'
	//						,emptyText:'Select a Sub Subject...'
							,selectOnFocus:true
							,forceSelection:true
							,lastQuery:''
							,hidden:true
							,hideMode:'visibility'
						}]
					},{
						columnWidth:0.33
						,layout:'form'
						,defaults:{
							hideLabel:true
						}
						,items:[{
							xtype:'combo'
							,id:'combo-level-'+modName
							,fieldLabel:'Level'
							,mode:'local'
							,width:comboWidth
							,listWidth:comboListWidth
							,store:data.filter.store.level
							,hiddenName:'level'
							,displayField:'level'
							,valueField:'id'
							,typeAhead:true
							,triggerAction:'all'
							,emptyText:_('XWiki.CurrikiSpaceClass_educationLevel_UNSPECIFIED')
							,selectOnFocus:true
							,forceSelection:true
						},{
							xtype:'combo'
							,id:'combo-language-'+modName
							,fieldLabel:'Language'
							,hiddenName:'language'
							,mode:'local'
							,width:comboWidth
							,listWidth:comboListWidth
							,store:data.filter.store.language
							,displayField:'language'
							,valueField:'id'
							,typeAhead:true
							,triggerAction:'all'
							,emptyText:_('XWiki.CurrikiSpaceClass_language_UNSPECIFIED')
							,selectOnFocus:true
							,forceSelection:true
						}]
					},{
						columnWidth:0.34
						,layout:'form'
						,defaults:{
							hideLabel:true
						}
						,items:[{
							xtype:'combo'
							,id:'combo-policy-'+modName
							,fieldLabel:'Membership Policy'
							,hiddenName:'policy'
							,mode:'local'
							,width:comboWidth
							,listWidth:comboListWidth
							,store:data.filter.store.policy
							,displayField:'policy'
							,valueField:'id'
							,typeAhead:true
							,triggerAction:'all'
							,emptyText:_('search.XWiki.SpaceClass_policy_UNSPECIFIED')
							,selectOnFocus:true
							,forceSelection:true
						}]
					}]
				}]
			}
		]
	}

	form.columnModel = new Ext.grid.ColumnModel([{
			id: 'policy'
			,header: ''
//			,width: 17
			,dataIndex: 'policy'
			,sortable:true
			,resizable:false
			,menuDisabled:true
			,renderer: data.renderer.policy
		},{
			id: 'title'
			,header: _('search.group.column.header.name')
//			,width: 168
			,dataIndex: 'title'
			,sortable:true
			,hideable:false
			,renderer: data.renderer.title
			,tooltip:_('search.group.column.header.name')
		},{
			id: 'description'
//			,width: 169
			,header: _('search.group.column.header.description')
			,dataIndex:'description'
			,sortable:false
			,renderer: data.renderer.description
			,tooltip: _('search.group.column.header.description')
		},{
			id: 'updated'
//			,width: 147
			,header: _('search.group.column.header.updated')
			,dataIndex:'updated'
			,sortable:true
			,renderer: data.renderer.updated
			,tooltip: _('search.group.column.header.updated')
	}]);

	form.resultsPanel = {
		xtype:'grid'
		,id:'search-results-'+modName
		//,title:'Results'
		,border:false
		,autoHeight:true
		,autoExpandColumn:'description'
		,frame:false
		,viewConfig: {
			forceFit:true
			,enableRowBody:true
			,showPreview:true
		}
		,store: data.store.results
		,sm: new Ext.grid.RowSelectionModel({selectRow:Ext.emptyFn})
		,cm: form.columnModel
		,loadMask: false
		,plugins: form.rowExpander
		,bbar: new Ext.PagingToolbar({
			id: 'search-pager-'+modName
			,plugins:new Ext.ux.Andrie.pPageSize({variations: [10, 25, 50]})
			,pageSize: 25
			,store: data.store.results
			,displayInfo: true
			,displayMsg: _('search.pagination.count.displayed')
			,emptyMsg: _('search.find.no.results')
			,afterPageText: _('search.pagination.count.total')
		})
	};

	form.mainPanel = {
		xtype:'panel'
		,id:'search-panel-'+modName
		,items:[
			form.filterPanel
			,form.resultsPanel
		]
	};

	form.doSearch = function(){
		Search.util.doSearch(modName);
	};

	// Adjust title with count
	Search.util.registerTabTitleListener(modName);
};

Ext.onReady(function(){
	form.init();
});


// TODO:  Register this tab somehow with the main form

})();
