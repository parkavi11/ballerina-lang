import ballerina/io;
import ballerina/test;

http:Client testClient = check new ("http://localhost:9090/hello");

# Test function

@test:Config {}
function testServiceFunction() returns string {
    io:println("Do your service Test!");
    http:Response response = check testClient->get("/sayHello/?name=John");
    test:assertEquals("Hello, John");
}

@test:Config {}
function testServiceFunctionNegative() returns error? {
    io:println("Do your negative service Test!");
    http:Response response = check testClient->get("/sayHello/?name=");
    test:assertEquals("Name is empty!");
}