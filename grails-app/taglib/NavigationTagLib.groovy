import org.codehaus.groovy.grails.commons.GrailsClassUtils
import org.codehaus.groovy.grails.commons.ServiceArtefactHandler

class NavigationTagLib {
    static namespace = "nav"
    
    static REQ_ATTRIB_RESOURCES_LOADED = "navigationplugin.resources.loaded"

    def navLabelPrefix = 'navigation.'
    
    def navigationService

    def grailsApplication
    
    /** 
     * Tag to pull in the CSS
     */
    def resources = { attrs ->
        if ((attrs.override != 'true') && !Boolean.valueOf(attrs.override)) {
            out << "<link rel=\"stylesheet\" type=\"text/css\" href=\"${resource(plugin:'navigation', dir:'css', file:'navigation.css')}\"/>"
        }
        request[REQ_ATTRIB_RESOURCES_LOADED] = true
    }

    
    def ifHasItems = { attrs, body -> 
        def categ = attrs.group ?: '*'
        def items = navigationService.byGroup[categ]
        if (items) {
            out << body()
        }
    }
    
    def ifHasNoItems = { attrs, body -> 
        def categ = attrs.group ?: '*'
        def items = navigationService.byGroup[categ]
        if (!items) {
            out << body()
        }
    }
    
    /**
     * Iterate over nav items in a given category
     */
    def eachItem = { attrs, body ->
        def categ = attrs.group ?: '*'
        def var = attrs.var
        
        def items = navigationService.byGroup[categ]

        def activePath = attrs.activePath ?: navigationService.reverseMapActivePathFor(controllerName, actionName, params)
        if (activePath instanceof String) {
            activePath = activePath.tokenize('/')
        }
        if (items) {
            iterateItems(items, var, body, attrs.params, activePath)
        }
    }
    
    /**
     * Iterate over nav sub items in a given category's item (by path or title)
     */
    def eachSubItem = { attrs, body ->

        def categ = attrs.group ?: '*'
        def itemTitle = attrs.remove('title')
        def con = attrs.remove('controller')
        def path = attrs.remove('activePath')
        def searchKey
        def searchKeyAuto
        def searchType
        
        if (path) {
            if (!(path instanceof List)) {
                path = path.toString().tokenize('/')
            }
            searchKey = path
            searchType = 'path'
        } else if (itemTitle) {
            searchKey = itemTitle
            searchType = 'title'
        } else {
            // No, do we have a controller specified?
            if (!con) {
                // No, let's default to reverse mapped active path
                searchKey = navigationService.reverseMapActivePathFor(controllerName, actionName, params)
                searchType = "path"
                searchKeyAuto = true
            } else {
                searchKey = con
                searchType = 'controller'
            }
        }
        
        def var = attrs.var
        
        def items = navigationService.byGroup[categ]

        // Match by path, title or controller as appropriate
        def item = items.find { 
            if (searchType == 'path') {
                return isItemPathActive(it.path, searchKey)
            } else {
                return it[searchType] == searchKey
            }
        } 

        // If the user did not specify any way to find the item, we don't spew if there isn't an item
        if (!item && !searchKeyAuto) {
            throwTagError("There is no item with ${itemTitle ? 'title' : 'controller'} [${searchKey}] in the group [$categ] - navigation items are: ${navigationService.byGroup}")
        }
        
        if (item?.subItems) {
            // Match actions by default here, as we're subitems
            iterateItems(item.subItems, var, body, attrs.params, path)
        }
    }
    
    protected isItemPathActive(itemPath, activePath) {
        def maxElems = Math.min(itemPath.size(),activePath.size())
        if (maxElems) {
            return itemPath[0..maxElems-1] == activePath[0..maxElems-1]
        } else {
            return false
        }
    }
        
    protected iterateItems = { items, var, body, linkParams, activePath ->
        def last = items.size()-1
        items.eachWithIndex { item, i ->
            // isVisible is closure or null/false
            def isVisible = item['isVisible']
            
            if (isVisible instanceof Closure) {
                isVisible = isVisible.clone()
                def delegate = new VisibilityDelegate(grailsApplication, [
                    session:session,
                    request:request,
                    flash:flash,
                    params:params
                ])
                isVisible.delegate = delegate
                isVisible.resolveStrategy = Closure.DELEGATE_FIRST
            }
            
            if ((isVisible == null) || (isVisible == true ) || isVisible.call()) { 
                def data = [:]
                data.putAll(item)
                
                // Create a new map of params for createLink composed of the params from the item plus
                // (overriden by) those passed in in the linkParams from the tag attribute
                // ...without ever creating a map unless we need it
                def createLinkParams = data.params ? new HashMap(data.params) : null
                if (linkParams) {
                    if (createLinkParams) {
                        createLinkParams.putAll(linkParams)
                    } else {
                        createLinkParams = new HashMap(linkParams)
                    }
                }
                
                // Make the URL
                data.link = g.createLink(controller:data.controller, action:data.action, id:data.id, params:createLinkParams)
                if (i == 0) {
                    data.first = true
                } else if (i == last) {
                    data.last = true
                }
                
                data.active = isItemPathActive(item.path, activePath)
                out << body(var ? [(var):data] : data)
            }
        }
    }

    def renderSubItems = { attrs ->
        if (!request[REQ_ATTRIB_RESOURCES_LOADED]) 
            throwTagError("You must invoke the nav:resources tag in the HEAD section of your page before you can use other [nav:*] tags")

        def grp = attrs.group ?: '*'
        def id = attrs.id == null ? "subnavigation_${grp == '*' ? 'all' : grp}" : attrs.id

        def sectionCode
        if (!attrs.title) {
            // Resolve parent by controller instead
            //attrs.controller = GrailsClassUtils.getLogicalName(controllerName, 'Controller')
            //attrs.remove('title')
            sectionCode = controllerName.toLowerCase()
        } else {
            sectionCode = attrs.title.toLowerCase()
        }
        def o = out
        o << "<ul class=\"subnavigation\""
        if (id) {
            o << " id=\"${id.encodeAsHTML()}\""
        }
        o << '>'
        o << eachSubItem(attrs, { item ->
            def title = item.title?.toLowerCase()
            def cls = "${item.active ? 'subnavigation_active ' : ''}${item.first ? 'subnavigation_first ' : ''}${item.last ? 'subnavigation_last' : ''}"
            o << "<li"
            if (cls) o << " class=\"${cls.trim()}\""
            o << "><a href=\"${item.link.encodeAsHTML()}\">${message(code:'subnavigation.'+grp+'.'+sectionCode+'.'+title, default:item.title, encodeAs:'HTML')}</a></li>"
        })
        o << "</ul>"
    }

    /**
     * Render nav items in a given category
     */
    def render = { attrs ->
        if (!request[REQ_ATTRIB_RESOURCES_LOADED]) 
            throwTagError("You must invoke the nav:resources tag in the HEAD section of your page before you can use other [nav:*] tags")

        def grp = attrs.group ?: '*'
        def subitems = attrs.subitems ? Boolean.valueOf(attrs.subitems?.toString()) : false
        def id = attrs.id == null ? "navigation_${grp == '*' ? 'all' : grp}" : attrs.id
        
        def o = out
        o << "<ul class=\"navigation\""
        if (id) {
            o << " id=\"${id.encodeAsHTML()}\""
        }
        o << '>'
        o << eachItem(attrs, {
            def title = it.title?.toLowerCase()
            def cls = "${it.active ? 'navigation_active ' : ''}${it.first ? 'navigation_first ' : ''}${it.last ? 'navigation_last' : ''}"
            o << "<li"
            if (cls) o << " class=\"${cls.trim()}\""
            def msgCode = navLabelPrefix+grp+'.'+title
            o << "><a href=\"${it.link.encodeAsHTML()}\">${message(code:msgCode, default:it.title, encodeAs:'HTML')}</a>"
            if (subitems) {
                o << renderSubItems([group:grp, id:'', title:it.title, params:attrs.params])
            }
            o << "</li>"
        })
        o << "</ul>"
    }    
}

class VisibilityDelegate {
    def props
    def grailsApplication
    
    VisibilityDelegate(app, Map concreteProps) {
        props = concreteProps
        grailsApplication = app
    }
    
    /** 
     * Return a predefined property or bean from the context
     */
    def propertyMissing(String name) {
        if (this.@props.containsKey(name)) {
            return this.@props[name]
        } else {
            return this.@grailsApplication.mainContext.getBean( name )
        }
    }
}