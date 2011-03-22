import grails.test.TagLibUnitTestCase

class NavigationTagLibTests extends TagLibUnitTestCase {
    /*
    
    These are commented out because I can't work out why Grails will not use our mocked service
    ...can't unit test really because we need the createLink tag to work in order to test this stuff
*/    
    void testEachItemByController() {
        tagLib.navigationService = [
            byGroup: ['tabs': [ 
                    [controller:'dummy', action:'index', path:['dummy', 'index']],
                    [controller:'dummy', action:'get', path:['dummy', 'get']]
                ]
            ],
            reverseMapActivePathFor: { con, act, params -> [con, act]}
        ]

        tagLib.metaClass.controllerName = 'something'
        tagLib.metaClass.actionName = 'something'
        tagLib.metaClass.createLink = { args -> "link" }

        tagLib.eachItem([controller:'dummy', group:'tabs'], {
            "Action:${it.action}|Active:${it.active}&"
        })
        def outcome = tagLib.out.toString().split('&')
        
        assertEquals 'Action:index|Active:false', outcome[0]
        assertEquals 'Action:get|Active:false', outcome[1]
    }

    void testEachItemActiveByPathDeepHit() {
        tagLib.navigationService = [
            byGroup: ['tabs': [ 
                    [controller:'dummy', action:'index', title:'Dummy', path:['dummy', 'index']],
                    [controller:'dummy', action:'get', title:'Get', path:['something', 'else', 'here'],
                        subItems:[ [action:'search', path:['something', 'else', 'here', 'searching']] ]
                    ]
                ]
            ],
            reverseMapActivePathFor: { con, act, params -> [con, act]}
        ]

        tagLib.metaClass.controllerName = 'something'
        tagLib.metaClass.actionName = 'something'
        tagLib.metaClass.createLink = { args -> "link" }

        def first = true
        tagLib.eachItem([activePath:'something/else/here', group:'tabs'], {
            "Action:${it.action}|Active:${it.active}|Title:${it.title}&"
        })
        def outcome = tagLib.out.toString().split('&')
        
        assertEquals 'Action:index|Active:false|Title:Dummy', outcome[0]
        assertEquals 'Action:get|Active:true|Title:Get', outcome[1]
    }
    
    void testEachSubItemActiveByPathDeepHit() {
        tagLib.navigationService = [
            byGroup: ['tabs': [ 
                    [controller:'dummy', action:'index', title:'Dummy', path:['dummy', 'index']],
                    [controller:'dummy', action:'get', title:'Get', path:['something', 'else', 'here'],
                        subItems:[ [action:'search', path:['something', 'else', 'here', 'searching']]]
                    ]
                ]
            ],
            reverseMapActivePathFor: { con, act, params -> [con, act]}
        ]

        tagLib.metaClass.controllerName = 'something'
        tagLib.metaClass.actionName = 'something'
        tagLib.metaClass.createLink = { args -> "link" }

        def first = true
        tagLib.eachSubItem([activePath:'something/else/here', group:'tabs'], {
            "Action:${it.action}|Active:${it.active}|Title:${it.title}&"
        })
        def outcome = tagLib.out.toString().split('&')
        
        assertEquals 'Action:search|Active:true|Title:null', outcome[0]
    }

    void testEachSubItemNotActiveByPathDeepHit() {
        tagLib.navigationService = [
            byGroup: ['tabs': [ 
                    [controller:'dummy', action:'index', title:'Dummy', path:['dummy', 'index']],
                    [controller:'dummy', action:'get', title:'Get', path:['something', 'else', 'here'],
                        subItems:[ [action:'search', path:['something', 'else', 'here', 'searching']]]
                    ]
                ]
            ],
            reverseMapActivePathFor: { con, act, params -> [con, act]}
        ]

        tagLib.metaClass.controllerName = 'something'
        tagLib.metaClass.actionName = 'something'
        tagLib.metaClass.createLink = { args -> "link" }

        def first = true
        tagLib.eachSubItem([activePath:'something/else/here/other', group:'tabs'], {
            "Action:${it.action}|Active:${it.active}|Title:${it.title}&"
        })
        def outcome = tagLib.out.toString().split('&')
        
        assertEquals 'Action:search|Active:false|Title:null', outcome[0]
    }
}
