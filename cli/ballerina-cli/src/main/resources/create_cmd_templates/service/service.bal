/*
 * Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import ballerina/http;

# A service representing a network-accessible API
# bound to port `9090`.
service /hello on new http:Listener(9090) {

    # A resource respresenting an invokable API method
    # accessible at `/hello/sayHello`.
    # + name - the input sting name
    # + return - "Hello, " and the input string name
    resource function get sayHello(string? name = ()) returns string {
        // Send a response back to the caller.
        if (name is string) {
            if !(name is "") {
                return "Hello, " + name;
            }
        }
        return "Hello, World!";
    }
}
