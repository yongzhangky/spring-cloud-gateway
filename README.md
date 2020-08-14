Kyligence Gateway
=====
### Quickly Manual
1. ${gateway.home}/conf/gateway.properties
    a. `kylin.gateway.datasource.type=file` is default.
    b. `kylin.gateway.datasource.route-table-file-path=${gateway.home}/conf/route_table` config route table.

2. route_table file 
    a. A GLOBAL route: {"id":111, "backends":["www.baidu.com", "wwww.google.com"], "resourceGroup":"default", "type":"GLOBAL"}
    b. A CUBE route: {"id":4, "backends":["10.1.2.56:7070"], "project":"p1", "resourceGroup":"common_query_1", "type":"CUBE"}, if http request header/url/body `p1` exist will route to backends
    c. A line a route, You can config multi routes.

3. route_table jdbc
    a. config `kylin.gateway.datasource.type=jdbc`

4. ${gateway.home}/bin/gateway.sh
    a. `./bin/gateway.sh start` to start gateway.
    
